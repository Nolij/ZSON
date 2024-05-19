package dev.nolij.zson;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a value in a JSON-like structure. Most methods in this class delegate to the underlying value.
 */
public final class ZsonValue {

	/**
	 * The comment for this value. If null, there is no comment.
	 */
	@Nullable
	public String comment;

	/**
	 * The underlying value.
	 */
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
