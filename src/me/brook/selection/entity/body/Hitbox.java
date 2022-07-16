package me.brook.selection.entity.body;

import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import me.brook.selection.entity.Agent;
import me.brook.selection.tools.Vector2;

public class Hitbox extends Area implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Vector2 low, high, origin;
	private float currentTheta;

	private Structure structure;
	
	private CellInfo[] cells;

	public Hitbox(Structure structure, Vector2 low, Vector2 high) {
		this.structure = structure;
		this.origin = low.add(high).divide(2);

		Vector2 a = low;
		Vector2 d = new Vector2(high.x, low.y);
		Vector2 c = high;
		Vector2 b = new Vector2(low.x, high.y);

		double minX = Math.min(a.x, Math.min(b.x, Math.min(c.x, d.x)));
		double minY = Math.min(a.y, Math.min(b.y, Math.min(c.y, d.y)));
		double maxX = Math.max(a.x, Math.max(b.x, Math.max(c.x, d.x)));
		double maxY = Math.max(a.y, Math.max(b.y, Math.max(c.y, d.y)));

		this.low = new Vector2(minX, minY);
		this.high = new Vector2(maxX, maxY);
	}

	public Hitbox(Structure structure, Rectangle2D.Double rectangle) {
		this(structure, new Vector2(rectangle.getMinX(), rectangle.getMinY()), new Vector2(rectangle.getMaxX(), rectangle.getMaxY()));
	}

	public void trans2late(Vector2 vec) {
		low = low.add(vec);
		high = high.add(vec);
		origin = origin.add(vec);
		build();
	}

	public Vector2 getLow() {
		return low;
	}

	public Vector2 getHigh() {
		return high;
	}

	public void setCurrentTheta(float currentTheta) {
		this.currentTheta = currentTheta;
	}

	public float getCurrentTheta() {
		return currentTheta;
	}

	public void setOrigin(Vector2 origin) {
		this.origin = origin;
	}

	public Vector2 getOrigin() {
		return origin.copy();
	}

	public float getWidth() {
		return high.x - low.x;
	}

	public float getMinX() {
		return low.x;
	}

	public float getMinY() {
		return low.y;
	}

	public float getMaxX() {
		return high.x;
	}

	public float getMaxY() {
		return high.y;
	}

	public float getHeight() {
		return high.y - low.y;
	}

	public boolean contains(float x, float y) {
		Vector2 v = new Vector2(x, y);
		v = v.rotate(origin, currentTheta); // perform same rotation to check if it is within bounds

		// rotate point ar

		return v.x > getMinX() && v.x < getMaxX() &&
				v.y > getMinY() && v.y < getMaxY();
	}

	public Vector2 getRotatedLow(Vector2 origin, double theta) {
		/*
		 * Get the four corners and transform them. Then, get the new lowest x/y from those rotated values
		 */
		Vector2 a = low.rotate(origin, theta);
		Vector2 b = new Vector2(low.x, high.y).rotate(origin, theta);
		Vector2 c = high.rotate(origin, theta);
		Vector2 d = new Vector2(high.x, low.y).rotate(origin, theta);

		double x = Math.min(a.x, Math.min(b.x, Math.min(c.x, d.x)));
		double y = Math.min(a.y, Math.min(b.y, Math.min(c.y, d.y)));

		return new Vector2(x, y);
	}

	public Vector2 getRotatedHigh(Vector2 origin, double theta) {
		/*
		 * Get the four corners and transform them. Then, get the new lowest x/y from those rotated values
		 */
		Vector2 a = low.rotate(origin, theta);
		Vector2 b = new Vector2(low.x, high.y).rotate(origin, theta);
		Vector2 c = high.rotate(origin, theta);
		Vector2 d = new Vector2(high.x, low.y).rotate(origin, theta);

		double x = Math.max(a.x, Math.max(b.x, Math.max(c.x, d.x)));
		double y = Math.max(a.y, Math.max(b.y, Math.max(c.y, d.y)));

		return new Vector2(x, y);
	}

	private Vector2[] createCorners() {
		Vector2 a = low.copy(); // bottom left
		Vector2 b = new Vector2(low.x, high.y); // top left
		Vector2 c = high.copy(); // top right
		Vector2 d = new Vector2(high.x, low.y); // bottom right

		return new Vector2[] { a, d, c, b };
	}

	public boolean build() {
		
		// build area
		Vector2[] myCorners = this.getRotatedCorners(this.getOrigin(), this.getCurrentTheta());
		Polygon p = new Polygon();
		for(int i = 0; i < myCorners.length; i++) {
			Vector2 vector2 = myCorners[i];
			p.addPoint((int) vector2.x, (int) vector2.y);
		}
		
		CellInfo[] myCells = new CellInfo[(int) this.structure.getStructure().size()];

		int index = 0;
		for(Segment seg : structure.getStructure()) {
			if(seg.getDevelopment() == 0)
				continue;
			
			// get light received at location of each cell
			Vector2 loc = this.getRotatedLow(new Vector2(), 0); // get bottom left of structure
			loc = loc.add(seg.getPosition().add(structure.getCoreOffset()).add(0.5, 0.5).multiply(Agent.getSizeOfSegment())); // get structure spot
			loc = loc.rotate(this.getOrigin(), this.getCurrentTheta()); // rotate it into position

			myCells[index++] = new CellInfo(loc, seg);
		}
		
		this.cells = new CellInfo[index];
		System.arraycopy(myCells, 0, this.cells, 0, index); // copy array adjusted to size	

		return true;
	}
	
	// https://stackoverflow.com/questions/10962379/how-to-check-intersection-between-2-rotated-rectangles
	public List<CellCollisionInfo> intersectsHitbox(Hitbox box) {
		
		
		// check if the distance from any two dots overlaps
		// get two closest points and move them away from each other
		CellInfo[] myCells = this.cells;
		CellInfo[] otherCells = box.cells;
		
		List<CellCollisionInfo> collisions = new ArrayList<>();

		double r2 = Agent.getSizeOfSegment() * Agent.getSegmentSizeExtension();
		r2 *= r2;
		for(CellInfo p1 : myCells) {	
			for(CellInfo p2 : otherCells) {
				double d = p1.loc.distanceToRaw(p2.loc);

				if(d < r2) {
					CellCollisionInfo cci = new CellCollisionInfo(p1, p2);
					collisions.add(cci);
				}
			}
		}

		return collisions;
	}
	
	public static class CellCollisionInfo {
		public CellInfo cell1, cell2;

		public CellCollisionInfo(CellInfo cell1, CellInfo cell2) {
			this.cell1 = cell1;
			this.cell2 = cell2;
		}
		
		
	}

	public Vector2[] getCorners() {
		return createCorners();
	}

	public Vector2[] getRotatedCorners(Vector2 origin, float theta) {
		if(origin == null) {
			theta = 0;
			origin = new Vector2();
		}
		Vector2[] corners = getCorners();

		for(int i = 0; i < corners.length; i++) {
			corners[i] = corners[i].rotate(origin, theta);
		}

		return corners;
	}
	
	public CellInfo[] getCells() {
		return cells;
	}

	public List<CellCollisionInfo> intersectsPoint(Vector2 location, double radius) {
		
		List<CellCollisionInfo> collisions = new ArrayList<>();
		
		double r2 = (radius + Agent.getSizeOfSegment() / 2);
		r2 *= r2;
		for(CellInfo p : this.cells) {
			
			if(p.loc.distanceToRaw(location) < r2)
				collisions.add(new CellCollisionInfo(p, new CellInfo(location, null)));
		}
		
		return collisions;
	}
	
	public static class CellInfo {
		public Vector2 loc;
		public Segment seg;
		
		public CellInfo(Vector2 loc, Segment seg) {
			this.loc = loc;
			this.seg = seg;
		}
		
		
	}

}
