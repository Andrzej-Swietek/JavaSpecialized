# IDE support

IntelliJ, Eclipse and VS Code analyse sources with their own front ends rather than javac, so without a
plugin they see neither the injected bridges nor the type rewriting. This is the situation Lombok is in
without its plugin.

What works with no plugin at all:

- Generated classes are ordinary sources under `build/generated/sources/annotationProcessor`, which a
  Gradle-imported project registers as a generated-sources root. After a build the IDE shows them with all
  members.
- Under the default `SPECIALIZE` mode, client code spelled `Box<Integer>` is valid generic Java for the
  IDE and compiles to `BoxInt`. Builds delegated to Gradle are the ones that count.
- `Box<int>` is red without a plugin.

## The IntelliJ plugin

`specialize-idea/` is a standalone Gradle build, because the IntelliJ Platform SDK is large:

```bash
cd specialize-idea
./gradlew buildPlugin        # build/distributions/specialize-idea.zip
./gradlew test               # fixture tests
```

Install it through *Settings → Plugins → ⚙ → Install Plugin from Disk*.

It contains two extensions. A `PsiAugmentProvider` adds the bridge overloads as light methods to every
`@Specialize` class once the generated class exists in the project, which is how the IDE resolves
`BoxInt b = Box.of(5)` and offers it in completion. A `HighlightInfoFilter` drops the "type argument cannot
be of primitive type" error on `Box<int>` where `Box` is a template, together with the follow-up errors
that spelling produces.

The plugin reads `@Specialize` from the project, `standsFor` aliases included, so it works for any template
rather than a fixed list. Bridges of templates with several `@Specialize.Param` parameters are not
augmented; the IDE resolves the generic factory there, which differs only in the result type.

## A private plugin repository

```bash
./gradlew updatePluginsXml -PpluginRepositoryUrl=https://repo.example.com/idea-plugins
```

writes `updatePlugins.xml` next to the zip. Put both on any HTTP server and add that URL under
*Settings → Plugins → ⚙ → Manage Plugin Repositories* to get updates without the JetBrains Marketplace.

Eclipse and VS Code would need the equivalent of Lombok's javaagent and are not covered.
