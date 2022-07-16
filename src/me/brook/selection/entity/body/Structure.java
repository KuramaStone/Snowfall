package me.brook.selection.entity.body;

import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import me.brook.selection.entity.Agent;
import me.brook.selection.tools.Vector2;

public class Structure implements Serializable {

	private static final long serialVersionUID = -4209408731996372090L;
	
	public List<Segment> structure;
	public String dna;
	private Rectangle2D bounds;
	private Vector2 coreOffset;

	private Map<String, List<Segment>> bytype;

	public Structure(String dna) {
		this.dna = dna;

		bytype = new HashMap<>();
		build();
	}
	
	public double getDevelopmentOfCell(int i) {
		return getDevelopmentOfCell(structure.get(i));
	}

	public double getDevelopmentOfCell(Segment segment) {
		return segment.getDevelopment();
	}
	
	public void setDevelopmentOfCell(int i, double value) {
		setDevelopmentOfCell(structure.get(i), value);
	}

	public void setDevelopmentOfCell(Segment segment, double value) {
		segment.setDevelopment(value);
	}
	
	public double getSumDevelopmentOfGrownCells() {
		double sum = 0;
		
		for(Segment seg : structure)
			sum += seg.getDevelopment();
		
		return sum;
	}

	public List<Segment> getStructure() {
		return structure;
	}

	public void build() {

		String[] chromosomes = dna.split("\\.");

		structure = new ArrayList<>();

		HashSet<Vector2> used = new HashSet<>();

		for(String chromo : chromosomes) {

			if(chromo.equals(CORE)) {
				used.add(new Vector2());
				structure.add(new Segment(CORE, new Vector2(), 0, 0));
				continue;
			}

			Vector2 directions = getDirectionsFromChromosome(chromo);
			if(used.contains(directions)) {
				// dont add multiple nodes to same location
				continue;
			}
			used.add(directions);

			// adjust bounds

			if(chromo.contains(PHOTO)) {
				structure.add(new Segment(PHOTO, directions, 0, 0));
			}
			else if(chromo.contains(CHEMO)) {
				structure.add(new Segment(CHEMO, directions, 0, 0));
			}
			else if(chromo.contains(HUNT)) {
				structure.add(new Segment(HUNT, directions, 0, 0));
			}
			else if(chromo.contains(THRUST)) {
				// use extra value to determine direction
				String str = chromo.replaceAll("[\\w^.-]", "");

				if(!str.isEmpty()) {
					char d = str.charAt(0);
					double theta;
					switch(d) {
						case '!' : {
							theta = Math.PI / 4 * 0;
							break;
						}
						case '@' : {
							theta = Math.PI / 4 * 1;
							break;
						}
						case '#' : {
							theta = Math.PI / 4 * 2;
							break;
						}
						case '$' : {
							theta = Math.PI / 4 * 3;
							break;
						}
						default:
							throw new IllegalArgumentException("Unexpected value: " + d);
					}

					structure.add(new Segment(THRUST, directions, theta, 0));
				}
			}

		}

		// calculate which segment faces are exposed and remove islands
		for(int i = 0; i < structure.size(); i++) {
			Segment segment = structure.get(i);
			boolean[] exposed = new boolean[4];

			// all used points are in the used list, so we can use that to test positions
			exposed[0] = used.contains(segment.getPosition().add(0, 1)); // north
			exposed[1] = used.contains(segment.getPosition().add(1, 0)); // east
			exposed[2] = used.contains(segment.getPosition().add(0, -1)); // south
			exposed[3] = used.contains(segment.getPosition().add(-1, 0)); // west

			segment.setExposedFaces(exposed);

		}

		// start from core and only add segments that are a neighbor. this removes floating cells
		for(int i = 0; i < structure.size(); i++) {
			Segment segment = structure.get(i);

			if(segment.getType().equals(Structure.CORE)) {

				List<Segment> neighborsOf = new ArrayList<>();
				getNeighborsOf(neighborsOf, segment);
				this.structure = neighborsOf; // also arranges shape into pattern moving away from body

				break;
			}

		}


		bytype.clear();
		for(Segment seg : structure) {
			List<Segment> list = bytype.getOrDefault(seg.getType(), new ArrayList<>());
			list.add(seg);
			bytype.put(seg.getType(), list);
		}

		recalculateBounds();
	}

	public Segment getByPosition(Vector2 position) {
		for(int i = 0; i < structure.size(); i++) {
			Segment segment = structure.get(i);

			if(segment.getPosition().equals(position))
				return segment;
		}

		return null;
	}

	private void getNeighborsOf(List<Segment> neighborsOf, Segment segment) {
		neighborsOf.add(segment);

		double r2 = Math.sqrt(2);
		for(Segment seg : structure) {
			if(neighborsOf.contains(seg))
				continue;
			
			if(seg.getPosition().distanceToSq(segment.getPosition()) <= r2) {
				getNeighborsOf(neighborsOf, seg);
			}
			
		}

	}

	public boolean hasType(String type) {
		return bytype.containsKey(type);
	}

	public List<Segment> getByType(String type) {
		return bytype.getOrDefault(type, new ArrayList<>());
	}

	public Rectangle2D getBounds() {
		return bounds;
	}

	private Vector2 getDirectionsFromChromosome(String chromo) {
		chromo = chromo.replaceAll("[\\D-]", ""); // remove letters

		Vector2 pos = new Vector2();
		for(char c : chromo.toCharArray()) {
			if(c == '0') {
				pos.y++;
			}
			else if(c == '1') {
				pos.x++;
			}
			else if(c == '2') {
				pos.y--;
			}
			else if(c == '3') {
				pos.x--;
			}
		}

		return pos;
	}

	public static enum Face {
		NORTH, EAST, SOUTH, WEST;
	}

	public static final String CORE = "A";
	public static final String PHOTO = "B";
	public static final String CHEMO = "C";
	public static final String THRUST = "D";
	public static final String HUNT = "E";

	public Vector2 getCoreOffset() {
		return coreOffset;
	}

	public int id() {
		int id = 0;

		for(Segment seg : structure) {
			id += seg.hashCode();
		}

		return id;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		Structure other = (Structure) obj;
		if(other.dna == null && this.dna != null)
			return false;
		if(!dna.equals(other.dna))
			return false;
		return true;
	}

	public void recalculateBounds() {
		float minX = 0, minY = 0, maxX = 0, maxY = 0;
		for(Segment segment : structure) {
			if(segment.getDevelopment() == 0)
				continue;
			
			Vector2 directions = segment.getPosition();
			if(minX > directions.x) {
				minX = directions.x;
			}
			else if(maxX < directions.x) {
				maxX = directions.x;
			}
			if(minY > directions.y) {
				minY = directions.y;
			}
			else if(maxY < directions.y) {
				maxY = directions.y;
			}
		}

		float w = (maxX - minX) + 1;
		float h = (maxY - minY) + 1;
		this.coreOffset = new Vector2(-minX, -minY);
		this.bounds = new Rectangle2D.Double(minX, minY, w, h);
	}

	public Rectangle2D.Double getMaxBounds() {
		float minX = 0, minY = 0, maxX = 0, maxY = 0;
		for(Segment segment : structure) {
			
			Vector2 directions = segment.getPosition();
			if(minX > directions.x) {
				minX = directions.x;
			}
			else if(maxX < directions.x) {
				maxX = directions.x;
			}
			if(minY > directions.y) {
				minY = directions.y;
			}
			else if(maxY < directions.y) {
				maxY = directions.y;
			}
		}

		float w = (maxX - minX) + 1;
		float h = (maxY - minY) + 1;
		return new Rectangle2D.Double(minX, minY, w, h);
	}

	public Vector2 getMaxCoreOffset() {
		float minX = 0, minY = 0, maxX = 0, maxY = 0;
		for(Segment segment : structure) {
			
			Vector2 directions = segment.getPosition();
			if(minX > directions.x) {
				minX = directions.x;
			}
			else if(maxX < directions.x) {
				maxX = directions.x;
			}
			if(minY > directions.y) {
				minY = directions.y;
			}
			else if(maxY < directions.y) {
				maxY = directions.y;
			}
		}

		return new Vector2(-minX, -minY);
	}
	
}