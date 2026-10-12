package lldpractice.design;

import java.util.List;

/**
 * One extracted type (class, interface, enum or record), its declared relationships and its
 * methods. {@code dependencies} lists other type names referenced as field or parameter types,
 * used by {@code StructuralEvaluator} to flag high coupling.
 */
public record TypeModel(
        String name,
        TypeKind kind,
        List<String> supertypes,
        List<MethodModel> methods,
        int fieldCount,
        int publicMutableFieldCount,
        List<String> dependencies,
        boolean nested
) {
    public TypeModel {
        supertypes = List.copyOf(supertypes);
        methods = List.copyOf(methods);
        dependencies = List.copyOf(dependencies);
    }

    public int instanceofCount() {
        return methods.stream().mapToInt(MethodModel::instanceofCount).sum();
    }

    public int switchCount() {
        return methods.stream().mapToInt(MethodModel::switchCount).sum();
    }

    public int totalBodyLines() {
        return methods.stream().mapToInt(MethodModel::bodyLineCount).sum();
    }
}
