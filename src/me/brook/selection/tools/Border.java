package me.brook.selection.tools;

import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.signum;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.dyn4j.collision.Bounds;
import org.dyn4j.collision.CollisionBody;
import org.dyn4j.geometry.AABB;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;

import me.brook.selection.World;
import me.brook.selection.entity.Agent;
import me.brook.selection.entity.Entity;

public class Border extends Area implements Serializable, Bounds {

	private static final long serialVersionUID = 7947366831664304294L;
	private List<Line2D> lines;
	private GeneralPath path;
	private Rectangle2D bounds2D;
	private Rectangle bounds;

	private Texture worldTexture;
	private Pixmap worldPixmap;

	private World world;

	public Border(World world) {
		this.world = world;
		lines = new ArrayList<>();
		path = new GeneralPath();
	}

	public void build() {
		bounds2D = path.getBounds2D();
		bounds = path.getBounds();
	}

	public void addWorldMap(int seed, int width, int height) {
		ShaderProgram terrainShader = new ShaderProgram(Gdx.files.internal("assets/shaders/terrainVertex.glsl"),
				Gdx.files.internal("assets/shaders/terrainFragment.glsl"));
		ShaderProgram wormShader = new ShaderProgram(Gdx.files.internal("assets/shaders/wormVertex.glsl"),
				Gdx.files.internal("assets/shaders/wormFragment.glsl"));

		if(!terrainShader.isCompiled()) {
			System.err.println("Terrain Shader");
			System.err.println(terrainShader.getLog());
			System.exit(1);
		}
		if(!wormShader.isCompiled()) {
			System.err.println("Worm Shader");
			System.err.println(wormShader.getLog());
			System.exit(1);
		}

		SpriteBatch batch = new SpriteBatch();

		OrthographicCamera camera = new OrthographicCamera(width, height);
		camera.translate(width / 2, height / 2);
		camera.update();

		worldPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
		framebuffer.begin();
		ScreenUtils.clear(1f, 0f, 0f, 1f);

		batch.setProjectionMatrix(camera.combined);
		batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ZERO);
		batch.begin();

		batch.setShader(terrainShader);
		terrainShader.setUniformf("width", width);
		terrainShader.setUniformf("height", height);
		terrainShader.setUniformi("seed", seed);

		batch.draw(world.getEngine().getDisplay().berrySprite, 0, 0, width, height);

		batch.end();

		ByteBuffer buf = worldPixmap.getPixels();
		Gdx.gl.glReadPixels(0, 0, width, height, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);


		Pixmap wormPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		// draw worms on pixmap
		int worms = 3;
		int index = 0;
		Noise wormMap = new Noise(3, (1.0 / 1800) * 50, 0.5, 2, 6543, 4214);
		Noise wormSize = new Noise(3, (1.0 / 1800) * 50, 0.5, 2, 5325, 4214);
		Random random = new Random();
		wormPixmap.setBlending(Blending.SourceOver); // disable blending of alpha
		for(int x = 0; x <= worms; x++) {
			for(int y = 0; y <= worms; y++) {
				random.setSeed(index * 12526);

				Vector2 start = new Vector2(((double) x / worms) * width, ((double) y / worms) * height);
				start = start.add(random.nextDouble() * (1.0 / (worms + 1)) * width * 0.1,
						random.nextDouble() * (1.0 / (worms + 1)) * height * 0.1);
				Vector2 pos = start.copy();

				wormPixmap.setColor(Color.WHITE);
				int length = 2000;
				for(int j = 0; j < length; j++) {
					double theta = wormMap.getSmoothNoiseAt(index * 1254251, j, seed);
					double size = Agent.getSegmentSizeExtension() * 25;
					size += size * (wormSize.getSmoothNoiseAt(x, y, 1)) / 2;

					wormPixmap.fillCircle((int) (pos.x - size / 2), (int) (pos.y - size / 2), (int) size);

					pos = pos.add(new Vector2((signum(theta) * (pow(abs(theta), 0.5))) * (Math.PI)).multiply(size / 2));
				}
				index++;
			}
		}

		PixmapIO.writePNG(Gdx.files.absolute("E:\\Programming\\Natural Selection\\map.png"), worldPixmap);

		Texture wormTexture = new Texture(wormPixmap);
		Texture terrainTexture = new Texture(worldPixmap);
		
		batch.begin();
		batch.setShader(wormShader);
		wormTexture.bind(1);
		wormShader.setUniformi("wormTexture", 1);
		Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
		
		batch.setColor(new Color(1f, 0.83f, .58f, 1f)); // cave color
		batch.draw(terrainTexture, 0, 0);
		
		batch.end();
		
		wormTexture.dispose();
		terrainTexture.dispose();

		buf = worldPixmap.getPixels();
		Gdx.gl.glReadPixels(0, 0, width, height, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
		
		worldTexture = new Texture(worldPixmap);

		framebuffer.end();
		framebuffer.dispose();
	}

	public static double cosInterpolate(double y1, double y2, double fraction) {
		double mu2 = (1 - cos(fraction * 3.141)) / 2;

		return (y1 * (1 - mu2) + y2 * mu2);
	}

	public double getCurveValueAt(Vector2 p1, Vector2 p2, double x) {
		double y = 0;

		if(p1.x > p2.x) {
			Vector2 temp = p1;
			p1 = p2;
			p2 = temp;
		}

		if(x < p1.x) {
			y = cosInterpolate(0, p1.y, x / p1.x); // interpolate between 0 and p1.y
		}
		else if(x > p1.x && x < p2.x) {
			y = cosInterpolate(p1.y, p2.y, (x - p1.x) / (p2.x - p1.x)); // interpolate between p1.y and p2.y
		}
		else { // interpolate between p2.y and 1
			y = cosInterpolate(p2.y, 1, (x - p2.x) / (1 - p2.x));
		}

		return y;
	}

	public boolean contains(double x, double y) {
		if(lines.size() <= 2) // need at least 3 sides to ever make a polygon
			return true;
		return path.contains(x, y);
	}

	public void addLine(Line2D line) {
		this.lines.add(line);
		path.append(line.getPathIterator(null), false);
	}

	public void addPolygon(Polygon polygon) {
		path.append(polygon, false);
		int x0 = polygon.xpoints[0];
		int y0 = polygon.ypoints[0];

		for(int j = 1; j < polygon.npoints; j++) {
			int x1 = polygon.xpoints[j];
			int y1 = polygon.ypoints[j];

			Line2D line = new Line2D.Double(x0, y0, x1, y1);
			lines.add(line);

			x0 = x1;
			y0 = y1;
		}

		// don't forget the last line which connects back to the first
		int x1 = polygon.xpoints[0];
		int y1 = polygon.ypoints[0];

		addLine(new Line2D.Double(x0, y0, x1, y1));
	}

	/**
	 * 
	 * @return Returns a copy of the lines arraylist
	 */
	public List<Line2D> getLines() {
		return new ArrayList<>(lines);
	}

	public Rectangle2D getBounds2D() {
		return bounds2D;
	}

	public Rectangle getBounds() {
		return bounds;
	}

	@Override
	public boolean contains(Point2D p) {
		return path.contains(p);
	}

	@Override
	public boolean intersects(double x, double y, double w, double h) {
		return path.intersects(x, y, w, h);
	}

	@Override
	public boolean intersects(Rectangle2D r) {
		return path.intersects(r);
	}

	@Override
	public boolean contains(double x, double y, double w, double h) {
		return path.contains(x, y, w, h);
	}

	@Override
	public boolean contains(Rectangle2D r) {
		return path.contains(r);
	}

	@Override
	public PathIterator getPathIterator(AffineTransform at) {
		return path.getPathIterator(at);
	}

	@Override
	public PathIterator getPathIterator(AffineTransform at, double flatness) {
		return path.getPathIterator(at, flatness);
	}

	public boolean doesLineIntersect(Line2D line) {
		for(Line2D line2 : lines) {
			if(line.intersectsLine(line2)) {
				return true;
			}
		}
		return false;
	}

	public Vector2 getClosestLineIntersection(Line2D.Double line) {
		List<Line2D> lines = getLines();

		Vector2 lineStart = new Vector2(line.getX1(), line.getY1());
		Vector2 closestLineIntersection = null;
		double closestDist = java.lang.Double.MAX_VALUE;
		for(Line2D l2 : lines) {
			if(l2.intersectsLine(line)) {
				Vector2 intersection = getIntersectPoint(l2, line);
				if(intersection == null)
					continue;
				double d = intersection.distanceToSq(lineStart);

				if(d < closestDist) {
					closestDist = d;
					closestLineIntersection = intersection;
				}
			}
		}

		return closestLineIntersection;
	}

	public static Vector2 getIntersectPoint(Line2D alpha, Line2D beta) {
		Vector2 ptA = new Vector2(alpha.getP1());
		Vector2 ptB = new Vector2(alpha.getP2());
		Vector2 ptC = new Vector2(beta.getP1());
		Vector2 ptD = new Vector2(beta.getP2());

		if(ptA == null || ptB == null || ptC == null || ptD == null) {
			return null;
		}

		Vector2 ptP = null;

		double denominator = (ptB.getX() - ptA.getX()) * (ptD.getY() - ptC.getY())
				- (ptB.getY() - ptA.getY()) * (ptD.getX() - ptC.getX());

		if(Math.copySign(denominator, 1.0) > 1e-6) {
			double numerator = (ptA.getY() - ptC.getY()) * (ptD.getX() - ptC.getX())
					- (ptA.getX() - ptC.getX()) * (ptD.getY() - ptC.getY());

			double r = numerator / denominator;

			ptP = new Vector2(
					ptA.getX() + r * (ptB.getX() - ptA.getX()),
					ptA.getY() + r * (ptB.getY() - ptA.getY()));
		}

		return ptP;
	}

	public void addLine(int x1, int y1, int x2, int y2) {
		this.addLine(new Line2D.Double(x1, y1, x2, y2));
	}

	public boolean intersects(AffineTransform at1, AffineTransform at2, Border shape) {

		Area a = new Area(this.getBounds2D());
		Area b = new Area(shape.getBounds2D());

		a.transform(at1);
		b.transform(at2);

		a.intersect(b);

		// check rough outline before checking complicated shape
		if(a.isEmpty()) {
			return false;
		}

		for(Line2D line : this.lines) {

			Rectangle2D transformedLine = at1.createTransformedShape(line).getBounds2D();

			for(Line2D line2 : shape.lines) {

				Rectangle2D transformedLine2 = at2.createTransformedShape(line2).getBounds2D();

				if(transformedLine.intersects(transformedLine2)) {
					return true;
				}
			}

		}
		return false;
	}

	@Override
	public String toString() {
		return "Border [lines=" + Arrays.toString(lines.toArray()) + ", path=" + path + "]";
	}

	public boolean doesLineIntersect(Vector2 v1, Vector2 v2) {
		return doesLineIntersect(new Line2D.Double(v1.toPoint(), v2.toPoint()));
	}

	@Override
	public void translate(double x, double y) {

	}

	@Override
	public void translate(org.dyn4j.geometry.Vector2 vector) {

	}

	@Override
	public void shift(org.dyn4j.geometry.Vector2 shift) {

	}

	@Override
	public org.dyn4j.geometry.Vector2 getTranslation() {
		return new org.dyn4j.geometry.Vector2(0, 0);
	}

	@Override
	public boolean isOutside(CollisionBody<?> body) {
		org.dyn4j.geometry.Vector2 c = body.getLocalCenter();
		return !contains(c.x, c.y);
	}

	@Override
	public boolean isOutside(AABB aabb) {
		return contains(aabb.getMinX(), aabb.getMinY()) &&
				contains(aabb.getMaxX(), aabb.getMaxY()) &&
				contains(aabb.getMaxX(), aabb.getMinY()) &&
				contains(aabb.getMinX(), aabb.getMaxY());
	}

	public boolean contains(Vector2 temp) {
		return contains(temp.x, temp.y);
	}

	public Pixmap getWorldPixmap() {
		return worldPixmap;
	}

	public Texture getWorldTexture() {
		return worldTexture;
	}

	public boolean intersects(Vector2 point) {
		point = point.copy();
		point.x -= bounds.getMinX();
		point.y -= bounds.getMinY();

		point.x = (float) ((point.x / bounds.getWidth()) * worldPixmap.getWidth());
		point.y = (float) (((1.0 - (point.y / bounds.getHeight()))) * worldPixmap.getHeight());

		Color color = new com.badlogic.gdx.graphics.Color(worldPixmap.getPixel((int) point.x, (int) point.y));
		float alpha = color.a;

		return alpha == 0;
	}

}
