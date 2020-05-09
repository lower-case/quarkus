package io.quarkus.deployment.steps;

import javax.inject.Inject;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.annotations.RegisterForReflection;

public class RegisterForReflectionBuildStep {

    private static final Logger log = Logger.getLogger(RegisterForReflectionBuildStep.class);

    @Inject
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @BuildStep
    public void build() {
        for (AnnotationInstance i : combinedIndexBuildItem.getIndex()
                .getAnnotations(DotName.createSimple(RegisterForReflection.class.getName()))) {

            boolean methods = getBooleanValue(i, "methods");
            boolean fields = getBooleanValue(i, "fields");
            boolean includeNested = getBooleanValue(i, "includeNested");
            AnnotationValue targetsValue = i.value("targets");

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (targetsValue == null) {
                ClassInfo classInfo = i.target().asClass();
                registerClass(classLoader, classInfo.name().toString(), methods, fields, includeNested);
            } else {
                Type[] targets = targetsValue.asClassArray();
                for (Type type : targets) {
                    registerClass(classLoader, type.name().toString(), methods, fields, includeNested);
                }
            }
        }
    }

    /**
     * BFS Recursive Method to register a class and it's inner classes for Reflection.
     */
    private void registerClass(ClassLoader classLoader, String className, boolean methods, boolean fields, boolean includeNested) {
        reflectiveClass.produce(new ReflectiveClassBuildItem(methods, fields, className));

        if (!includeNested) {
            return;
        }

        try {
            Class<?>[] declaredClasses = classLoader.loadClass(className).getDeclaredClasses();
            for (Class<?> clazz : declaredClasses) {
                registerClass(classLoader, clazz.getName(), methods, fields, true);
            }
        } catch (ClassNotFoundException e) {
            log.errorf(e, "Failed to load Class %s", className);
        }
    }

    private static boolean getBooleanValue(AnnotationInstance i, String name) {
        return i.value(name) == null || i.value(name).asBoolean();
    }
}
