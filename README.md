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

<!--- TODO: make a tutorial for serializing objects and the annotations that help in doing so --->

## License

This project is licensed under OSL-3.0. For more information, see [LICENSE](LICENSE).
