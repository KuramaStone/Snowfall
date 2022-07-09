package me.brook.selection.tools;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class SpatialHashing<T extends QuadSortable> {

	private int width;
	private int divisions;
	private int divisionLength;
	private HashMap<T, HashSet<Chunk<T>>> all;
	private HashMap<Coords, Chunk<T>> chunks;

	private int searchInstance = 0;

	private Class<T> clazz;
	private T[] emptyArray;

	public SpatialHashing(Class<T> clazz, int width, int divisions) {
		this.clazz = clazz;
		if(divisions < 1)
			divisions = 1;

		this.width = width;
		this.divisions = divisions;
		this.divisionLength = width / divisions;
		emptyArray = (T[]) Array.newInstance(clazz, 1);

		chunks = new HashMap<>();
		all = new HashMap<>(divisions * divisions);

		// int s = 14;
		// for(int x = -s; x < s; x++) {
		// for(int y = -s; y < s; y++) {
		// Coords key = new Coords(x, y);
		// chunks.put(key, new Chunk<>(key, divisionLength));
		// }
		// }
	}

	public void clear() {
		all.clear();
		chunks.clear();
	}

	public T[] query(Rectangle2D rectangle) {
		// get the distance between diagonals
		Coords key = getCoordsFromRawVector(new Vector2(rectangle.getMinX(), rectangle.getMinY()));

		int sx = (int) Math.ceil(rectangle.getWidth() / (divisionLength));
		int sy = (int) Math.ceil(rectangle.getHeight() / (divisionLength));

		Coords chunkCoords;
		int x1, y1;
		Chunk<T> chunk;

		// increase search instance. Clients with the same instance have already been added, so ignore them. this is to ignore duplicates.
		this.searchInstance++;
		T[] found = emptyArray.clone();

		int index = 0;
		for(int x = 0; x <= sx; x++) {
			x1 = x + key.x;
			for(int y = 0; y <= sy; y++) {
				y1 = y + key.y;

				chunkCoords = new Coords(x1, y1);
				chunk = chunks.get(chunkCoords);

				if(chunk == null)
					continue;

				for(Client<T> c : chunk.entities.values()) {
					// if(rectangle.contains(e.getLocation().toPoint()))
					if(c.id != searchInstance) {
//						if(c.client.intersects(rectangle)) {
							found[index++] = c.client;
							found = checkAndExpand(found, index); // expand array if needed
							c.id = this.searchInstance;
						}
//					}
				}

			}
		}

		return found;
	}
	
	private T[] checkAndExpand(T[] array, int index) {

		if(index == array.length) {
			T[] dest = (T[]) Array.newInstance(clazz, array.length * 2);
			System.arraycopy(array, 0, dest, 0, index);
			return dest;
		}

		return array;
	}

	public void store(T t, Vector2 origin, double size) {
		if(t == null) {
			throw new IllegalArgumentException("Cannot add to spatial hash; T is null.");
		}

		if(all.containsKey(t)) {
			remove(t);
		}

		Coords key = getCoordsFromRawVector(t.getLocation());

		Chunk<T> chunk = chunks.get(key);
		if(chunk == null) {
			chunk = new Chunk<>(key, divisionLength);
			chunks.put(key, chunk);
		}

		chunk.addEntity(t, searchInstance);
		int squares = (int) Math.ceil(Math.ceil(size / 2) / (divisionLength));

		if(size < divisionLength)
			squares = 0;

		Coords chunkCoords;
		int x1, y1;

		HashSet<Chunk<T>> list = new HashSet<>();
		for(int x = -squares; x <= squares; x++) {
			x1 = x + key.x;
			for(int y = -squares; y <= squares; y++) {
				y1 = y + key.y;

				chunkCoords = new Coords(x1, y1);
				chunk = chunks.get(chunkCoords);

				if(chunk == null) {
					chunk = new Chunk<>(chunkCoords, divisionLength);
					chunks.put(chunkCoords, chunk);
				}

				chunk.addEntity(t, searchInstance);
				list.add(chunk);
			}
		}

		list.add(chunk);
		all.put(t, list);
	}

	public Coords getCoordsFromRawVector(Vector2 location) {
		int x = (int) (location.x / (divisionLength));
		int y = (int) (location.y / (divisionLength));
		if(location.x < 0) {
			x -= 1;
		}
		if(location.y < 0) {
			y -= 1;
		}
		return new Coords(x, y);
	}

	public void remove(T t) {
		HashSet<Chunk<T>> chunks = all.get(t);
		if(chunks == null)
			return;

		for(Chunk<T> c : chunks) {

			if(c.entities.size() == 1) {
				this.chunks.remove(c.getKey());
			}
			else {
				c.removeEntity(t);
			}
		}

		chunks.clear();
		all.remove(t);

	}

	public int getHashKey(Vector2 location) {
		return (int) (location.x + location.y * width);
	}

	public Map<T, HashSet<Chunk<T>>> getAll() {
		return all;
	}

	public static class Coords implements Serializable {
		private static final long serialVersionUID = 808307105900983996L;

		public int x, y;

		public Coords(int x, int y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + x;
			result = prime * result + y;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			Coords other = (Coords) obj;
			if(x != other.x)
				return false;
			if(y != other.y)
				return false;
			return true;
		}

	}

	public static class Chunk<T extends QuadSortable> {

		private int size;
		private Coords key;
		private Rectangle bounds;
		private Map<T, Client<T>> entities;

		public Chunk(Coords key, int size) {
			this.key = key;
			this.size = size;
			this.bounds = new Rectangle((int) key.x, (int) key.y, size, size);
			this.entities = new HashMap<>();
		}

		public void addEntity(T t, int id) {
			entities.put(t, new Client<T>(t, id));
		}

		public void removeEntity(T t) {
			entities.remove(t);
		}

		public int getChunkX() {
			return (int) key.x;
		}

		public int getChunkY() {
			return (int) key.y;
		}

		public Coords getKey() {
			return key;
		}

		public int getSize() {
			return size;
		}

		public Rectangle getBounds() {
			return bounds;
		}

		public Map<T, Client<T>> getEntities() {
			return entities;
		}

	}

	public static class Client<T> {
		public Client(T t, int id2) {
			this.client = t;
			this.id = id2;
		}

		public T client;
		public int id;

	}

	public Map<Coords, Chunk<T>> getChunks() {
		return chunks;
	}

	public int getSize() {
		return width;
	}

	public int getDivisionLength() {
		return divisionLength;
	}

//	public static void main(String[] args) {
//		renderTest();
//	}
//
//	static void renderTest() {
//		JFrame frame = new JFrame();
//		frame.setSize(500, 500);
//		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		frame.setVisible(true);
//
//		SpatialHashing<TestSortable> hash = new SpatialHashing<TestSortable>(TestSortable.class, 500, 5);
//
//		TestSortable test = new TestSortable(new Vector2(200, 200), 5);
//		hash.store(test, test.getLocation(), 1);
//		Graphics2D g2 = (Graphics2D) frame.getGraphics();
//
//		Collection<TestSortable> list = new ArrayList<>();
//		frame.addMouseListener(new MouseAdapter() {
//
//			TestSortable tracking;
//
//			@Override
//			public void mouseReleased(MouseEvent e) {
//				Vector2 location = new Vector2(e.getPoint().x, e.getPoint().y);
//
//				// add new sortable
//				tracking = new TestSortable(location, 5);
//				try {
//					list.add(tracking);
//				}
//				catch(Exception e1) {
//				}
//
//			}
//
//		});
//
//		for(int i = 0; i < 50; i++) {
//			float theta = (float) ((Math.PI * 2) * (i / 50.0));
//
//			for(float d = 0; d < 20; d++) {
//				Vector2 v = new Vector2(theta).multiply(d*10);
//
//				list.add(new TestSortable(v.add(250, 250), 5));
//			}
//		}
//
//		BufferedImage buffer = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
//
//		Hitbox hitbox = new Hitbox(new Vector2(150, 150), new Vector2(350, 350));
//
//		Graphics2D g3 = (Graphics2D) buffer.getGraphics();
//		float time = 0;
//		while(true) {
//			if(!list.isEmpty()) {
//				for(TestSortable t : list) {
//					hash.store(t, t.getLocation(), 1);
//				}
//				list.clear();
//			}
//
//			time += 0.002;
//			hitbox.setCurrentTheta((float) ((Math.PI / 2 * time) % (Math.PI * 2)));
//			
//			g3.setColor(Color.WHITE);
//			g3.fillRect(0, 0, 500, 500);
//			
//			Polygon polygon = new Polygon();
//			Vector2[] corners = hitbox.getRotatedCorners();
//			for(int i = 0; i < corners.length; i++) {
//				Vector2 p1 = corners[i];
//
//				polygon.addPoint((int) p1.x, (int) p1.y);
//			}
//			
//
//			List<TestSortable> near = Arrays.asList(hash.query(hitbox));
//			g3.setColor(Color.GREEN);
//			g3.drawRect(200, 200, 100, 100);
//
//			for(QuadSortable ts : near) {
//				if(ts == null)
//					continue;
//				g3.fillOval((int) (ts.getLocation().x - 5), (int) (ts.getLocation().y - 5), 10, 10);
//			}
//
//			g3.setColor(Color.BLACK);
//			new ArrayList<>(hash.getAll().keySet()).forEach(
//					ts -> {
//						if(!near.contains(ts))
//							g3.fillOval((int) (ts.getLocation().x - 5), (int) (ts.getLocation().y - 5), 10, 10);
//					});
//
//			g3.setColor(Color.RED);
////			g3.drawPolygon(polygon);
//			g2.drawImage(buffer, 0, 0, frame.getWidth(), frame.getHeight(), null);
//		}
//
//	}

	public static void drawSpatialMap(Graphics2D g3, SpatialHashing<? extends QuadSortable> hash) {
		for(Chunk<?> c : hash.getChunks().values()) {
			int size = c.size;
			g3.draw(c.getBounds());
			g3.drawString(c.getChunkX() + " " + c.getChunkY(), c.getChunkX() * size + 10, c.getChunkY() * size + 10);
		}
	}

	public boolean contains(T e) {
		return all.containsKey(e);
	}

}
