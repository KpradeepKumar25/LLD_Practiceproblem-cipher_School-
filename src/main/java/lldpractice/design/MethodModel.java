package lldpractice.design;

import java.util.List;

/** A method signature plus coarse body statistics, extracted from a submission's code. */
public record MethodModel(
        String name,
        boolean isPublic,
        boolean isStatic,
        List<String> parameterTypes,
        int bodyLineCount,
        int instanceofCount,
        int switchCount
) {
    public MethodModel {
        parameterTypes = List.copyOf(parameterTypes);
    }
}
