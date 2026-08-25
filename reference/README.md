Reference material
==================

`mods/` — third-party mod jars supplied as examples or shortcuts. **Gitignored.** These are
other people's work; they are read here, never redistributed and never copied from.

`extracted/` — unzipped or decompiled output from those jars. Also gitignored.

To read one:

    unzip -o -d extracted/<name> mods/<name>.jar        # data/ and assets/ JSON is the useful part
    java -jar <vineflower> extracted/<name> extracted/<name>-src   # only if the Java matters

Vineflower is already in the Gradle cache under `~/.gradle/caches/modules-2/files-2.1/org.vineflower`.
