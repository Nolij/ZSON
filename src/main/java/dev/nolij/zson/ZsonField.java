package dev.nolij.zson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ZsonField is an annotation that can be used to specify properties
 * about a field in a class that is being serialized or deserialized to ZSON.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ZsonField {

	/**
	 * @return a comment that describes this field, to be included in the ZSON output.
	 */
	String comment() default ZsonValue.NO_COMMENT;

	/**
	 * @return whether to include this field when (de)serializing, even if it is private or static.
	 */
	boolean include() default false;

	/**
	 * @return whether to exclude this field when (de)serializing.
	 */
	boolean exclude() default false;
}
