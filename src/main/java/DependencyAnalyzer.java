import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.*;

public class DependencyAnalyzer {

    public static void main (String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: <Classname> <dependency1.jar> <dependency2.jar> ...");
            return;
        }

        String classname = args[0].replace('.', '/');

        Map<JarFile, Set<String>> jars = new HashMap<>(); // map jarfile to all classnames in that jar

        // put all available classes into a map
        for (int i = 1; i < args.length; i++) {
            try {
                JarFile cur = new JarFile(args[i]);
                Set<String> entries = new HashSet<>();
                cur.stream().forEach(entry -> {
                    if (entry.getName().endsWith(".class")) {
                        entries.add(entry.getName().substring(0, entry.getName().length() - 6));
                    }
                });
                jars.put(cur, entries);
            } catch (IOException e) {
                System.out.println("File" + args[i] + " can't be opened");
                closeJars(jars);
            }
        }

        Set<String> visited = new HashSet<>();
        Queue<byte[]> toVisit = new LinkedList<>();

        byte[] initialClass = findClassInJars(jars, classname);
        if (initialClass == null) {
            System.out.println("false: initial class is not in jars");
            closeJars(jars);
            return;
        }
        toVisit.offer(initialClass);
        visited.add(classname);

        boolean canBeLaunched = true;
        Set<String> missingDeps = new HashSet<>();
        // start bfs on dependencies
        while (!toVisit.isEmpty()) {
            byte[] nextClass = toVisit.poll();

            Set<String> deps = analyzeDependencies(nextClass);
            // remove all java.* dependencies and basic datatypes
            deps.removeIf(dep -> dep.startsWith("java")
                    || dep.startsWith("void")
                    || dep.startsWith("int")
                    || dep.startsWith("long")
                    || dep.startsWith("double")
                    || dep.startsWith("float")
                    || dep.startsWith("short")
                    || dep.startsWith("byte")
                    || dep.startsWith("boolean")
                    || dep.startsWith("char")
            );
            deps.removeIf(visited::contains);
            visited.addAll(deps);

            for (String dep : deps) {
                byte[] classData = findClassInJars(jars, dep);
                if (classData == null) {
                    missingDeps.add(dep.replace('/', '.'));
                    canBeLaunched = false;
                } else {
                    toVisit.offer(classData);
                }
            }

        }
        if (!canBeLaunched) {
            System.out.println("false\nMissing dependencies: " + missingDeps);
        } else {
            System.out.println(true);
        }
    }

    /**
     * cleanup method to close all previously open jar files
     * @param jars open jars
     */
    private static void closeJars(Map<JarFile, Set<String>> jars) {
        for (JarFile jarFile : jars.keySet()) {
            try {
                jarFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * finds a class by its string classname in given jars
     * @param jars available jars
     * @param classname class to find
     * @return byte array with class data, null if class not found
     */

    private static byte[] findClassInJars(Map<JarFile, Set<String>> jars, String classname) {
        for (JarFile jarFile : jars.keySet()) {
            JarEntry entry = jarFile.getJarEntry(classname + ".class");
            if (entry != null) {
                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    return inputStream.readAllBytes();
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * statically analyzes .class file and finds dependencies using ASM framework
     * @param classData class bytes to scan
     * @return set of string names of dependencies
     */
    private static Set<String> analyzeDependencies(byte[] classData) {
        Set<String> referencedClasses = new HashSet<>();
        ClassReader classReader = new ClassReader(classData);

        classReader.accept(new ClassVisitor(Opcodes.ASM9) {

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                if (superName != null) referencedClasses.add(superName);
                if (interfaces != null) for (String iface : interfaces) referencedClasses.add(iface);
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                referencedClasses.add(Type.getType(descriptor).getClassName());
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                Type methodType = Type.getMethodType(descriptor);
                referencedClasses.add(methodType.getReturnType().getClassName());
                for (Type argType : methodType.getArgumentTypes()) {
                    referencedClasses.add(argType.getClassName());
                }
                if (exceptions != null) for (String exception : exceptions) referencedClasses.add(exception);

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        referencedClasses.add(owner);
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        referencedClasses.add(type);
                        super.visitTypeInsn(opcode, type);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type) {
                            referencedClasses.add(((Type) value).getClassName());
                        }
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        referencedClasses.add(Type.getType(descriptor).getClassName());
                        return super.visitAnnotation(descriptor, visible);
                    }

                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                        referencedClasses.add(Type.getType(descriptor).getClassName());
                        return super.visitParameterAnnotation(parameter, descriptor, visible);
                    }

                };
            }
            @Override
            public void visitAttribute(Attribute attr) {
                if (!attr.isUnknown()) {
                    referencedClasses.add(attr.type);
                }
                super.visitAttribute(attr);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                referencedClasses.add(Type.getType(descriptor).getClassName());
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                referencedClasses.add(Type.getType(descriptor).getClassName());
                return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
            }

        }, 0);

        referencedClasses.removeIf(cl -> cl.startsWith("[I"));
        Set<String> result = new HashSet<>();
        // handle array types
        for (String cl : referencedClasses) {
            if (cl.startsWith("[L")) {
                result.add(cl.substring(2, cl.length() - 1).replace('.', '/'));
            } else if (cl.endsWith("[]")) {
                result.add(cl.substring(0, cl.length() - 2).replace('.', '/'));
            }
            else {
                result.add(cl.replace('.', '/'));
            }
        }

        return result;
    }
}