package com.delaunay;

import android.util.Log;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.content.Context;
import android.graphics.*;

import java.util.*;


public class MyActivity extends Activity {

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		View customView = new TriView(this);
		setContentView(customView);
	}

	public class TriView extends View {

		public boolean onTouchEvent(MotionEvent event) {

			Log.d("action", "" + event.getActionMasked());

			if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {

				Vertex newVx = new Vertex(event.getX() / (float) this.getWidth() * 2.f - 1.f,
						event.getY() / (float) this.getHeight() * 2.f - 1.f);

				_triangulation.AddVertex(newVx);
				invalidate();
				return true;
			}

			return super.onTouchEvent(event);
		}

		protected void onDraw(Canvas canvas)
		{
			super.onDraw(canvas);

			TriDrawer drawer = new TriDrawer(canvas);

			canvas.drawColor(Color.WHITE);
			_triangulation.Draw(drawer);
		}

		public TriView(Context context)
		{
			super(context);
			_triangulation = new Triangulation();
		}

		private Triangulation _triangulation;

	}

	public class TriDrawer implements IDrawable {

		public void DrawTri (Vertex [] vertices, Vertex center, float radius)
		{
			Paint paint = new Paint();
			paint.setAntiAlias(true);

			for (int i = 0; i < 3; ++ i) {

				Vertex from = ConvertToWindowed(vertices[i]), to = ConvertToWindowed(vertices[(i + 1) % 3]);

				_canvas.drawLine(from.x, from.y, to.x, to.y, paint);
			}

			Vertex	top = ConvertToWindowed(new Vertex(center.x - radius, center.y - radius)),
					bottom = ConvertToWindowed(new Vertex(center.x + radius, center.y + radius));

			RectF rect = new RectF(top.x, top.y, bottom.x, bottom.y);

			paint.setColor(Color.argb(50, 200, 0, 0));
			paint.setStyle(Paint.Style.STROKE);
			_canvas.drawArc(rect, -1.f, 360.f, false, paint);
		}

		public TriDrawer(Canvas canvas)
		{
			_canvas = canvas;
		}

		private Vertex ConvertToWindowed(Vertex vx)
		{
			return new Vertex((vx.x * 0.5f + 0.5f) * (float)_canvas.getWidth(), (vx.y * 0.5f + 0.5f) *  (float)_canvas.getHeight());
		}

		private Canvas _canvas;
	}

	public class Triangulation
	{
		public void Draw(IDrawable drawable)
		{
			for (Tri tri : _tris)
			{
				drawable.DrawTri(new Vertex[] { _points.get(tri.indices[0]), _points.get(tri.indices[1]), _points.get(tri.indices[2]) },
				tri.center, (float)Math.sqrt(tri.radiusSq));
			}
		}

		public Triangulation()
		{
			_points.add(new Vertex(-1.0F, -1.0F));
			_points.add(new Vertex(1.0F, -1.0F));
			_points.add(new Vertex(1.0F, 1.0F));
			_points.add(new Vertex(-1.0F, 1.0F));
			_points.add(new Vertex(-0.5F, -0.25F));

			AddTri (new int[] { 4, 0, 1 });
			AddTri (new int[] { 4, 1, 2 });
			AddTri (new int[] { 4, 2, 3 });
			AddTri (new int[] { 4, 3, 0 });
		}

		/**
		 * Определяет является ли входная точка внутренней точкой входного треугольника
		 *
		 * @return true if this instance is embracing the specified vx tri; otherwise, false
		 * @param vx Вершина
		 * @param tri Треугольник
		 */
		private boolean IsEmbracing(Vertex vx, Tri tri)
		{
			boolean isInner = true;

			for (int i = 0; i < 3; ++ i) {

				Vertex edge = CalcVector (_points.get(tri.indices [(i + 1) % 3]), _points.get(tri.indices [i]));
				Vertex toVx = CalcVector (vx, _points.get(tri.indices [i]));

				if (CrossProduct (edge, toVx) < 0.0F)
					isInner = false;
			}

			return isInner;
		}

		/**
		 * Осуществляет поиск треугольников, нарушающих условие Делоне для новой входной точки
		 *
		 * @return список треугольников
		 * @param vx Вершина
		 */
		private NearTris FindNearTris(Vertex vx)
		{
			NearTris nearTris = new NearTris();

			for(Tri tri : _tris)
			{
				Vertex diff = CalcVector (tri.center, vx);
				float distSq = diff.x * diff.x + diff.y * diff.y;

				if (distSq < tri.radiusSq) {
					nearTris.tris.add(tri);
					Log.d ("near: ", " " + tri.indices[0] + " " + tri.indices[1] + " " + tri.indices[2]);
				}
			}



			return nearTris;
		}

		/**
		 * Фильтрует ребра не соответвующие обходу по часовой стрелке, т.е. только ребра контура
		 *
		 * @return Новый список ребер куда входят только ребра контура
		 * @param edges Все ребра
		 * @param wrongEdges Ребра не соотв. обходу по часовой стрелке, т.е. ребра не принадлежащие контуру
		 */
		private LinkedList<Edge> ClearFromWrongEdges(List<Edge> edges, Set< Edge > wrongEdges)
		{
			LinkedList<Edge> cleared = new LinkedList<Edge> ();

			for (Edge edge : edges) {
				if (!wrongEdges.contains(edge)) cleared.add(edge);
			}

			return cleared;
		}

		/**
		 * Классифицируем ребра, на основе этого заполняем списки.
		 *
		 * @param iCurVx Начальная точка ребра
		 * @param iNextVx Конечная точка ребра
		 * @param edges Правильные контурные ребра направленные (iCurVx, iNextVx) по часовой стрелке
		 * @param wrongEdges Ребра против часовой стрелки не могут быть контурными
		 */
		private void ClassifyByDirection(Vertex vx, int iCurVx, int iNextVx, List<Edge> edges, Set< Edge > wrongEdges) {

			Vertex curEdge = CalcVector (_points.get(iCurVx), vx);
			Vertex nextEdge = CalcVector (_points.get(iNextVx), vx);

			float crossProduct = CrossProduct (nextEdge, curEdge);

			Edge edge = new Edge(iCurVx, iNextVx);

			if (crossProduct < 0.0F) {
				edges.add (edge);
			} else
			{
				Edge reverse = new Edge(iNextVx, iCurVx);
				wrongEdges.add(reverse);
			}
		}

		/**
		 * Формируем контур из совсем близких треугольников - треугольников нарушающих уловие Делоне
		 *
		 * @return Ребра контура
		 * @param nearTris Близкие треугольники нарушающие условие Делоне
		 * @param vx Новая входная вершина
		 */
		private LinkedList<Edge> FormEdgesFromNearTris(NearTris nearTris, Vertex vx)
		{
			List<Edge> edges = new LinkedList<Edge>();
			Set<Edge> wrongEdges = new TreeSet<Edge>();

			for (Tri tri : nearTris.tris) {

				for (int i = 0; i < 3; ++ i) {

					int iCurVx = tri.indices [i], iNextVx = tri.indices [(i + 1) % 3];
					ClassifyByDirection (vx, iCurVx, iNextVx, edges, wrongEdges);
				}
			}
			return ClearFromWrongEdges(edges, wrongEdges);
		}

		private class Tri
		{
			/**
			 * Индексы вершин образующих треугольник
			 */
			public int[] indices;

			/**
			 * Центр описаной вокруг треугольника окружности
			 */
			public Vertex center;

			/**
			 * Квадрат радиуса описанной вокруг треугольника окружности
			 */
			public float radiusSq;

			public Tri(int[] indices, Vertex center, float radiusSq)
			{
				this.indices = indices;
				this.center = center;
				this.radiusSq = radiusSq;
			}

			public int hashCode()
			{
				return ("1." + indices[0] + " 2." + indices[1] + " 3." + indices[2]).hashCode();
			}

			public boolean equals(Tri op)
			{
				return indices[0] == op.indices[0] && indices[1] == op.indices[1] && indices[2] == op.indices[2];
			}
		}

		private class Edge implements Comparable<Edge>
		{
			public int iFrom, iTo;

			public Edge(int iFrom, int iTo)
			{
				this.iFrom = iFrom;
				this.iTo = iTo;
			}

			public int compareTo(Edge e)
			{
				if (iFrom == e.iFrom)
					return iTo - e.iTo;
				else return iFrom - e.iFrom;
			}

			public boolean equals(Edge e)
			{
				return this.iFrom == e.iFrom && this.iTo == e.iTo;
			}
		}

		/**
		 * Массив всех вершин
		 */
		private ArrayList<Vertex> _points = new ArrayList<Vertex>();

		/**
		 * Список всех треугольников
		 */
		private LinkedHashSet<Tri> _tris = new LinkedHashSet<Tri>();

		private class NearTris {

			public List< Tri > tris = new LinkedList< Tri >();
		}

		private float DotProduct(Vertex a, Vertex b)
		{
			return a.x * b.x + a.y * b.y;
		}

		private Vertex CalcVector(Vertex to, Vertex from)
		{
			return new Vertex(to.x - from.x, to.y - from.y);
		}

		private float CrossProduct(Vertex a, Vertex b)
		{
			return (a.x * b.y) - (a.y * b.x);
		}


		private void RemoveNearTris(NearTris nearTris)
		{
			for (Tri tri : nearTris.tris) {
				Log.d ("remove tri: ", "" + tri.indices[0] + " " + tri.indices[1] + " " + tri.indices[2]);
				_tris.remove(tri);
			}
		}

		private void AddTrisAroundVertex(int iNewVx, List<Edge> edges)
		{
			for(Edge edge : edges) {
				AddTri(new int[] { iNewVx, edge.iFrom, edge.iTo });
			}
		}

		private void TessellateTri(int iNewVx, Tri tri)
		{
			for (int i = 0; i < 3; ++ i) {
				AddTri(new int[] { iNewVx, tri.indices[i], tri.indices[(i + 1) % 3] });
			}

			_tris.remove(tri);
		}

		/**
		* Добавляет указанную вершину в треангуляцию, производит ее перестроение для соотв. условию Делоне
		*
		* @param vx Вершина
		*/
		public void AddVertex(Vertex vx)
		{
			_points.add (vx);

			Log.d("add vertex", "" + vx.x + " " + vx.y);
			int iNewVx = _points.size() - 1;

			NearTris nearTris = FindNearTris (vx);

			if (nearTris.tris.size() > 1)
			{
				LinkedList<Edge> edges = FormEdgesFromNearTris (nearTris, vx);
				RemoveNearTris(nearTris);
				AddTrisAroundVertex(iNewVx, edges);

				Log.d ("overall tris: ", Integer.toString(_tris.size()));
			} else
			{
				TessellateTri(iNewVx, nearTris.tris.get(0));
			}
		}

		/**
		* Вычисляет атрибуты треугольника и заносит их в массивы.
		*
		* @param indices Индексы вершин треугьлника в массиве вершин _vertices
		*/
		private void AddTri(int[] indices)
		{
			Log.d ("add tri: ", "" + indices[0] + " " + indices[1] + " " + indices[2]);

			Vertex v1 = _points.get(indices[0]), v2 = _points.get(indices[1]), v3 = _points.get(indices[2]);

			float r1 = v1.x * v1.x + v1.y * v1.y;
			float r2 = v2.x * v2.x + v2.y * v2.y;
			float r3 = v3.x * v3.x + v3.y * v3.y;

			float a = v1.x * v2.y + v1.y * v3.x + v2.x * v3.y - v3.x * v2.y - v2.x * v1.y - v3.y * v1.x;
			float b = r1 * v2.y + v1.y * r3 + r2 * v3.y - r3 * v2.y - r2 * v1.y - v3.y * r1;
			float c = r1 * v2.x + v1.x * r3 + r2 * v3.x - r3 * v2.x - r2 * v1.x - v3.x * r1;

			Vertex center = new Vertex(b / (a * 2.0F), -c / (a * 2.0F));
			Vertex delta = new Vertex(center.x - v1.x, center.y - v1.y);
			float radiusSq = delta.x * delta.x + delta.y * delta.y;

			_tris.add(new Tri(indices, center, radiusSq));
		}

	}

	public class Vertex
	{
		public float x, y;

		public Vertex(float x, float y)
		{
			this.x = x;
			this.y = y;
		}
	}

	public interface IDrawable
	{
		void DrawTri (Vertex [] vertices, Vertex center, float radius);
	}

}
