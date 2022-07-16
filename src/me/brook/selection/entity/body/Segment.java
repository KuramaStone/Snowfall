package me.brook.selection.entity.body;

import java.io.Serializable;
import java.util.Arrays;

import me.brook.selection.entity.body.Structure.Face;
import me.brook.selection.tools.Vector2;

public class Segment implements Serializable {
	
	private static final long serialVersionUID = -2407627176895952766L;
	
	private String type;
	private Vector2 position;
	private double value;
	private double strength;
	private boolean[] exposedFaces;
	private double development;

	public Segment(String type, Vector2 position, double value, double strength) {
		this.type = type;
		this.position = position;
		this.value = value;
		this.strength = strength;
		exposedFaces = new boolean[4];
	}
	
	public void setDevelopment(double development) {
		this.development = development;
	}
	
	public double getDevelopment() {
		return development;
	}

	public String getType() {
		return type;
	}

	public Vector2 getPosition() {
		return position;
	}
	
	public double getValue() {
		return value;
	}

	public double getStrengthValue() {
		return strength;
	}
	
	public void setStrength(double strength) {
		this.strength = strength;
	}

	public boolean hasNeighbor(Face face) {
		
		int index = switch(face) {
			case NORTH : {
				yield 0;
			}
			case EAST : {
				yield 1;
			}
			case SOUTH : {
				yield 2;
			}
			case WEST : {
				yield 3;
			}
			default:
				throw new IllegalArgumentException("Unexpected value: " + face);
		};
		
		return exposedFaces[index];
	}
	
	public void setExposedFaces(boolean[] exposedFaces) {
		this.exposedFaces = exposedFaces;
	}
	
	public boolean[] getExposedFaces() {
		return exposedFaces;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((position == null) ? 0 : position.hashCode());
		long temp;
		temp = Double.doubleToLongBits(strength);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		temp = Double.doubleToLongBits(value);
		result = prime * result + (int) (temp ^ (temp >>> 32));
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
		Segment other = (Segment) obj;
		if(position == null) {
			if(other.position != null)
				return false;
		}
		else if(!position.equals(other.position))
			return false;
		if(Double.doubleToLongBits(strength) != Double.doubleToLongBits(other.strength))
			return false;
		if(type == null) {
			if(other.type != null)
				return false;
		}
		else if(!type.equals(other.type))
			return false;
		if(Double.doubleToLongBits(value) != Double.doubleToLongBits(other.value))
			return false;
		return true;
	}

}