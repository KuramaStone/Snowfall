package me.brook.selection.tools;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.Serializable;

import me.brook.selection.tools.hash.Vec3I;

public class Vector2 implements Serializable {

	private static final long serialVersionUID = -5346411102465369657L;

	public float x, y, z;

	public Vector2() {
		this(0, 0);
	}

	public Vector2(Point2D point) {
		this(point.getX(), point.getY());
	}

	public Vector2(double x, double y) {
		this.x = (float) x;
		this.y = (float) y;
	}

	public Vector2(double theta) {
		this(Math.cos(theta), Math.sin(theta));
	}

	public float distanceToSq(Vector2 loc) {
		return (float) Math.sqrt(Math.pow(this.x - loc.x, 2) + Math.pow(this.y - loc.y, 2));
	}

	public float distanceToRaw(Vector2 loc) {
		return (float) (Math.pow(this.x - loc.x, 2) + Math.pow(this.y - loc.y, 2));
	}

	public float getX() {
		return x;
	}

	public void setX(float x) {
		this.x = (float) x;
	}

	public double getY() {
		return y;
	}

	public void setY(float y) {
		this.y = (float) y;
	}

	public double getZ() {
		return z;
	}

	public void setZ(float z) {
		this.z = (float) z;
	}

	public Vector2 add(Vector2 loc) {
		return new Vector2(this.x + loc.x, this.y + loc.y);
	}

	public Vector2 add(double x, double y) {
		return new Vector2(this.x + x, this.y + y);
	}

	public Vector2 subtract(Vector2 loc) {
		return new Vector2(this.x - loc.x, this.y - loc.y);
	}

	public Vector2 multiply(double multiple) {
		return new Vector2(this.x * multiple, this.y * multiple);
	}

	public Vector2 multiply(Vector2 vector) {
		return new Vector2(this.x * vector.x, this.y * vector.y);
	}

	public Vector2 divide(double d) {
		return new Vector2(this.x / d, this.y / d);
	}

	public Vector2 divide(Vector2 vector) {
		return new Vector2(this.x / vector.x, this.y / vector.y);
	}

	public Vector2 normalize() {
		float highest = x;

		if(y > highest) {
			highest = y;
		}

		if(z > highest) {
			highest = z;
		}

		if(highest == 0) {
			return copy();
		}

		return divide(highest);
	}

	@Override
	public String toString() {
		return "Location [x=" + x + ", y=" + y + "]";
	}

	public Vector2 copy() {
		return new Vector2(x, y);
	}

	public Point toPoint() {
		return new Point((int) x, (int) y);
	}

	public float dot(Vector2 v2) {
		return this.x * v2.x + this.y * v2.y;
	}

	public float atan2(Vector2 vector) {
		return (float) Math.atan2(y - vector.y, x - vector.x);
	}

	public Vector2 rotate(Vector2 center, double theta) {
		double cs = Math.cos(theta);
		double sn = Math.sin(theta);

		double translated_x = x - center.x;
		double translated_y = y - center.y;

		double result_x = translated_x * cs - translated_y * sn;
		double result_y = translated_x * sn + translated_y * cs;

		result_x += center.x;
		result_y += center.y;

		return new Vector2(result_x, result_y);
	}

	public com.badlogic.gdx.math.Vector2 toGDX() {
		return new com.badlogic.gdx.math.Vector2(x, y);
	}

	public org.dyn4j.geometry.Vector2 toDyn4j() {
		return new org.dyn4j.geometry.Vector2(this.x, this.y);
	}

	public static Vector2 fromDyn4j(org.dyn4j.geometry.Vector2 v) {
		return new Vector2(v.x, v.y);
	}

	public double length() {
		return distanceToSq(new Vector2());
	}

	public Vec3I toVec3I() {
		return new Vec3I((int) x, (int) y, (int) z);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(x);
		result = prime * result + Float.floatToIntBits(y);
		result = prime * result + Float.floatToIntBits(z);
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
		Vector2 other = (Vector2) obj;
		if(Float.floatToIntBits(x) != Float.floatToIntBits(other.x))
			return false;
		if(Float.floatToIntBits(y) != Float.floatToIntBits(other.y))
			return false;
		if(Float.floatToIntBits(z) != Float.floatToIntBits(other.z))
			return false;
		return true;
	}

	public boolean isFinite() {
		return Float.isFinite(x) && Float.isFinite(y);
	}

}