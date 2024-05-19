# IMPORTANT LICENSE NOTICE

By using this project in any form, you hereby give your "express assent" for the terms of the license of this
project (see [License](#license)), and acknowledge that I (the author of this project) have fulfilled my obligation
under the license to "make a reasonable effort under the circumstances to obtain the express assent of recipients to
the terms of this License".

# ZSON
A tiny JSON5 parsing library for Java 8, with a focus on simplicity and minimizing size.

## Usage
First, include the library in your project. You can do this by adding the following to your build.gradle(.kts):
<details>
<summary>Kotlin</summary>

```kotlin
repositories {
    maven("https://maven.blamejared.com")
}

dependencies {
    implementation("dev.nolij:zson:version")
}
```
</details>
<details>
<summary>Groovy</summary>

```groovy
repositories {
    maven { url 'https://maven.blamejared.com' }
}

dependencies {
    implementation 'dev.nolij:zson:version'
}
```
</details>
Replace `version` with the version of the library you want to use.
You can find the latest version on the [releases page](https://github.com/Nolij/ZSON/releases).

Then, you can use the library like so:
```java
import dev.nolij.zson.Zson; // contains static methods for parsing and writing JSON
import dev.nolij.zson.ZsonWriter; // contains methods for writing JSON
import dev.nolij.zson.ZsonParser; // contains static methods for parsing JSON
import dev.nolij.zson.ZsonValue; // represents a JSON value with a comment
import java.util.Map;

import static dev.nolij.zson.Zson.*;

public class ZsonExample {
    public static void main(String[] args) {
        // Parse a JSON string
        String json = "{\"key\": \"value\"}";
        Map<String, ZsonValue> zson = ZsonParser.parseString(json);
        System.out.println(zson.get("key")); // value

        // Write a JSON string
        ZsonWriter writer = new ZsonWriter().withIndent("  ").withExpandArrays(false);
		Map<String, ZsonValue> map = object( // Zson.object()
                entry("key", "comment", 4),
                entry("arr", "look, arrays work too!", array(1, 2, 3)),
                entry("obj", "and objects!", object(
                        entry("key", "value")
                )),
                entry("null", "comments can also\nbe miltiple lines", null)
        );
		System.out.println(jsonString);
	}
}

```

This prints out:
```json5
{
  // comment
  "key": 4,
  // look, arrays work too!
  "arr": [ 1, 2, 3, ],
  // and objects!
  "obj": {
    "key": "value", 
  },
  // comments can also
  // be multiple lines
  "null": null,
}
```

## Serializing objects
ZSON can serialize objects to JSON using reflection. Here's an example:
```java
import dev.nolij.zson.Zson;
import dev.nolij.zson.Comment;
import dev.nolij.zson.Include;
import dev.nolij.zson.Exclude;
import dev.nolij.zson.ZsonValue;

public class Example {
	@Comment("This is a comment")
	public String key = "value";
	
	@Include
    private int number = 4;
	
	@Exclude
    public String excluded = "this won't be included";
	
	public static void main(String[] args) {
		Example example = new Example();
        Map<String, ZsonValue> zson = Zson.obj2map(example);
		System.out.println(new ZsonWriter().stringify(zson));
	}
}
```

This prints out:
```json5
{
  // This is a comment
  "key": "value",
  "number": 4,
}
```

Use the `@Comment` annotation to add a comment to a field, and the `@Include` and `@Exclude` annotations to include or exclude fields from serialization.
By default, all public fields are included, and all private fields are excluded. If they are annotated with `@Include`, static fields will be serialized but not deserialized.

Also see the [tests](src/test/java/ZsonTest.java) for more examples.

## Building
To build the project, run `./gradlew build` on Unix or `gradlew.bat build` on Windows.

## License

This project is licensed under OSL-3.0. For more information, see [LICENSE](LICENSE).
