package dev.nolij.zson;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ZsonValue {

	@Nullable
	public String comment;

	@Nullable
	public Object value;
	
	public ZsonValue(@Nullable String comment, @Nullable Object value) {
		this.comment = comment;
		this.value = value;
	}
	
	public ZsonValue(Object value) {
		this(null, value);
	}

	@Override
	public int hashCode() {
		return value == null ? 0 : value.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ZsonValue v && Objects.equals(this.value, v.value)) || Objects.equals(other, this.value);
	}

	@Override
	public String toString() {
		// TODO: maybe the comment should be added here too
		return String.valueOf(value);
	}
}
