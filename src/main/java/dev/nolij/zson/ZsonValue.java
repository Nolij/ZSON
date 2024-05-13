package dev.nolij.zson;

import java.util.Objects;

public final class ZsonValue {
	public String comment;
	public Object value;
	
	public ZsonValue(String comment, Object value) {
		this.comment = comment;
		this.value = value;
	}
	
	public ZsonValue(Object value) {
		this(null, value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return (o instanceof ZsonValue v && Objects.equals(this.value, v.value)) || Objects.equals(o, this.value);
	}

	@Override
	public String toString() {
		// TODO: maybe the comment should be added here too
		return value.toString();
	}
}
