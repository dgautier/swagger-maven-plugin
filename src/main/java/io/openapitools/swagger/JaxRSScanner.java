package io.openapitools.swagger;

import com.google.common.collect.Sets;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.apache.maven.plugin.logging.Log;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;

import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scan for classes with {@link Path} annotation or {@link OpenAPIDefinition}
 * annotation, and for {@link Application} instances.
 */
class JaxRSScanner {

    private final Log log;

    private final Set<String> resourcePackages;

    private final  Set<String> schemaPackages;

    private final boolean useResourcePackagesChildren;

    public JaxRSScanner(Log log, Set<String> resourcePackages, Set<String> schemaPackages, Boolean useResourcePackagesChildren) {
        this.log = log;
        this.resourcePackages = resourcePackages == null ? Collections.emptySet() : new HashSet<>(resourcePackages);
        this.schemaPackages = schemaPackages == null ? Collections.emptySet() : new HashSet<>(schemaPackages);
        log.info("Parsing schema packages " + this.schemaPackages.size() );
        this.useResourcePackagesChildren = useResourcePackagesChildren != null && useResourcePackagesChildren;
    }

    Application applicationInstance() {
        ConfigurationBuilder config = ConfigurationBuilder
                .build(resourcePackages)
                .setScanners(new ResourcesScanner(), new TypeAnnotationsScanner(), new SubTypesScanner());
        Reflections reflections = new Reflections(config);
        Set<Class<? extends Application>> applicationClasses = reflections.getSubTypesOf(Application.class)
                .stream()
                .filter(this::filterClassByResourcePackages)
                .collect(Collectors.toSet());
        if (applicationClasses.isEmpty()) {
            return null;
        }
        if (applicationClasses.size() > 1) {
            log.warn("More than one javax.ws.rs.core.Application classes found on the classpath, skipping");
            return null;
        }
        return ClassUtils.createInstance(applicationClasses.iterator().next());
    }

    Set<Class<?>> schemas() {
        Set<Class<?>> classes = Sets.newHashSet();
        schemaPackages.forEach(packageName -> {
            Set<Class<?>> schemaClasses = findClasses(packageName);
            log.info("Found " + schemaClasses.size() + " beans in package : " + packageName);
            classes.addAll(schemaClasses);
        });
        return classes;
    }

    Set<Class<?>> classes() {
        ConfigurationBuilder config = ConfigurationBuilder
                .build(resourcePackages)
                .setScanners(new ResourcesScanner(), new TypeAnnotationsScanner(), new SubTypesScanner());
        Reflections reflections = new Reflections(config);
        Stream<Class<?>> apiClasses = reflections.getTypesAnnotatedWith(Path.class)
                .stream()
                .filter(this::filterClassByResourcePackages);
        Stream<Class<?>> defClasses = reflections.getTypesAnnotatedWith(OpenAPIDefinition.class)
                .stream()
                .filter(this::filterClassByResourcePackages);

        Set<Class<?>> classes =
                Stream.concat(apiClasses, defClasses).collect(Collectors.toSet());
        return classes;
    }

    private boolean filterClassByResourcePackages(Class<?> cls) {
        return resourcePackages.isEmpty()
                || resourcePackages.contains(cls.getPackage().getName())
                || (useResourcePackagesChildren && resourcePackages.stream().anyMatch(p -> cls.getPackage().getName().startsWith(p)));
    }

    private Set<Class<?>> findClasses(String packageName) {
        Reflections reflections = new Reflections(packageName, new SubTypesScanner(false));
        return reflections.getSubTypesOf(Object.class)
                .stream()
                .collect(Collectors.toSet());
    }

}
