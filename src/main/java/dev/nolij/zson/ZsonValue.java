package dev.nolij.zson;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a value in a JSON-like structure. Most methods in this class delegate to the underlying value.
 */
public final class ZsonValue {

	/**
	 * The value for {@link #comment} when there is no comment, represented as the null character.
	 */
	public static final String NO_COMMENT = "\0";

	/**
	 * The comment for this value. If the comment is {@link #NO_COMMENT}, then there is no comment.
	 */
	@NotNull
	public String comment;

	/**
	 * The underlying value.
	 */
	@Nullable
	public Object value;
	
	public ZsonValue(@NotNull String comment, @Nullable Object value) {
		this.comment = comment;
		this.value = value;
	}
	
	public ZsonValue(Object value) {
		this(NO_COMMENT, value);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(value);
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
