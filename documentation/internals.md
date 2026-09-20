# Internals

The processor is an ordinary `javax.annotation.processing.Processor` plus an auto-started javac `Plugin`.
javac trees are mutated in place, which is what an AST rewriter does; everything else is immutable data and
`Optional`-returning lookups. 68 of the 77 files are under 150 lines.

`tutorial/README.md` (in Polish) walks through the compiler phases, `JCTree` and the
`TreeScanner`/`TreeTranslator` pattern with excerpts from the JDK sources.

## One round

```
register @Specialize / @Specialized / @SpecializeWith
  → scan usages (autoscan)
  → rewrite each unit: ConstEval → Unroll → TailRec → Inline → use sites
  → generate the specializations, repeating while new ones appear
  → inject bridges into the templates
```

The use-site rewrite runs last, so code inserted by inlining is rewritten too. Bridges are injected at the
end, when every specialization that will exist is known.

## Packages

| package | content |
|---|---|
| `dev.specialize.processor` | `SpecializeProcessor` (entry), `SpecializePlugin` (makes `Box<int>` parse), `RoundProcessor`, `Javac`, `ModuleAccess`, `ProcessingEnvironments`, `Diagnostics`, `SourceDump` |
| `…model` | `TargetType` (`PrimitiveTarget` / `ReferenceTarget`), `TargetTuple`, `Template` (`SourceTemplate` / `BinaryTemplate`), `NamePattern`, `Specialization` |
| `…registry` | `SpecRegistry` with `SpecializationIndex` and `Aliases`, `AnnotationReader`, `AnnotationValues`, `TemplateSources`, `UsageScanner`, `ClassIndex` |
| `…resolve` | `NameResolver` / `CompilationUnitResolver` (names before attribution), `ClassScope`, `Annotations`, `Match`, `TreeUtil` |
| `…specialize` | `TemplateSpecializer` with `SpecializedClass`, `Substitution`, `TranslationScope`, `BoxingDepth`, `PrimCallRewriter`, `PrimHelper`, `FunctionalInterfaces`, `MethodSignatures`, `SpecializationGenerator`, `SourceRenderer`, `BridgeInjector`, `MethodSpecializer`, `PrimitiveArgumentRewriter` |
| `…rewrite` | `UseSiteRewriter`, `SpecLookup`, `SpecUse`, `VariableScope`, `ScopedTranslator`, `Retargeter` |
| `…inline` | `InlineRegistry`, `InlineMethodFactory`, `InlineBodyParser`, `InlineBodyValidator`, `InlineBodyStamp`, `Qualifier`, `InlineMethod` with `BodySubstitution`, `LambdaInliner`, `InlinedBlocks`, `LocalNames`, `InlineExpander` |
| `…tailrec` | `TailRecursionEliminator`, `TailCallLoop` |
| `…unroll` | `LoopUnroller`, `ConstantLoop`, `LoopBodyCheck`, `LoopVariableSubstituter` |
| `…consteval` | `ConstEvalRewriter`, `ConstantEvaluator`, `EvaluationResult`, `SideFileManager`, `Literals` |

## Module access

The processor needs `com.sun.tools.javac.*`, which `jdk.compiler` does not export. It opens those packages
for itself through `sun.misc.Unsafe` and `IMPL_LOOKUP`, the way Lombok does, and does nothing when they are
already exported. See [getting started](getting-started.md) for exporting them yourself.

## Tests

`./gradlew build` runs 125 tests and enforces 100% line and branch coverage in every module. The processor
is tested by compiling snippets and the example sources in-process through `javax.tools`, both from source
and against previously compiled class files, and by asserting on `javap -c` output where the bytecode is
the point.
