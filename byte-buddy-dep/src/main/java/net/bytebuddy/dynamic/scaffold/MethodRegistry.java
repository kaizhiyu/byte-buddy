package net.bytebuddy.dynamic.scaffold;

import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.Transformer;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import net.bytebuddy.implementation.attribute.MethodAttributeAppender;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.LatentMatcher;
import net.bytebuddy.utility.CompoundList;

import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * A method registry is responsible for storing information on how a method is intercepted. 方法注册表负责存储有关如何截获方法的信息
 */
public interface MethodRegistry {

    /**
     * Prepends the given method definition to this method registry, i.e. this configuration is applied first. 将给定的方法定义前置到此方法注册表，即首先应用此配置
     *
     * @param methodMatcher            A matcher to identify any method that this definition concerns. 用于标识此定义所涉及的任何方法的匹配器
     * @param handler                  The handler to instrument any matched method. 对任何匹配方法进行指桩的处理程序
     * @param attributeAppenderFactory A method attribute appender to apply to any matched method. 应用于任何匹配方法的方法属性附加器
     * @param transformer              The method transformer to be applied to implemented methods. 要应用于实现方法的方法转换器
     * @return An adapted version of this method registry. 此方法注册表的改编版本
     */
    MethodRegistry prepend(LatentMatcher<? super MethodDescription> methodMatcher,
                           Handler handler,
                           MethodAttributeAppender.Factory attributeAppenderFactory,
                           Transformer<MethodDescription> transformer);

    /**
     * Appends the given method definition to this method registry, i.e. this configuration is applied last. 将给定的方法定义附加到此方法注册表，即此配置最后应用
     *
     * @param methodMatcher            A matcher to identify all entries that are to be matched.
     * @param handler                  The handler to instrument any matched method.
     * @param attributeAppenderFactory A method attribute appender to apply to any matched method.
     * @param transformer              The method transformer to be applied to implemented methods.
     * @return An adapted version of this method registry.
     */
    MethodRegistry append(LatentMatcher<? super MethodDescription> methodMatcher,
                          Handler handler,
                          MethodAttributeAppender.Factory attributeAppenderFactory,
                          Transformer<MethodDescription> transformer);

    /**
     * Prepares this method registry. 准备此方法注册
     *
     * @param instrumentedType    The instrumented type that should be created. 应创建的插桩类型
     * @param methodGraphCompiler The method graph compiler to be used for analyzing the fully assembled instrumented type. 用于分析完全组装的插入指令类型的方法图编译器
     * @param typeValidation      Determines if a type should be explicitly validated. 确定是否应显式验证类型
     * @param ignoredMethods      A filter that only matches methods that should be instrumented.
     * @return A prepared version of this method registry.
     */
    Prepared prepare(InstrumentedType instrumentedType,
                     MethodGraph.Compiler methodGraphCompiler,
                     TypeValidation typeValidation,
                     LatentMatcher<? super MethodDescription> ignoredMethods);

    /**
     * A handler for implementing a method. 实现方法的处理程序
     */
    interface Handler extends InstrumentedType.Prepareable {

        /**
         * Compiles this handler.
         *
         * @param implementationTarget The implementation target to compile this handler for. 编译此处理程序的 implementation target
         * @return A compiled handler.
         */
        Handler.Compiled compile(Implementation.Target implementationTarget);

        /**
         * A handler for defining an abstract or native method. 用于定义抽象或本机方法的处理器
         */
        enum ForAbstractMethod implements Handler, Compiled {

            /**
             * The singleton instance.
             */
            INSTANCE;

            @Override
            public InstrumentedType prepare(InstrumentedType instrumentedType) {
                return instrumentedType;
            }

            @Override
            public Compiled compile(Implementation.Target implementationTarget) {
                return this;
            }

            @Override
            public TypeWriter.MethodPool.Record assemble(MethodDescription methodDescription, MethodAttributeAppender attributeAppender, Visibility visibility) {
                return new TypeWriter.MethodPool.Record.ForDefinedMethod.WithoutBody(methodDescription, attributeAppender, visibility);
            }
        }

        /**
         * A handler for implementing a visibility bridge. 用于实现可见性桥的处理器
         */
        enum ForVisibilityBridge implements Handler {

            /**
             * The singleton instance.
             */
            INSTANCE;

            @Override
            public InstrumentedType prepare(InstrumentedType instrumentedType) {
                throw new IllegalStateException("A visibility bridge handler must not apply any preparations");
            }

            @Override
            public Compiled compile(Implementation.Target implementationTarget) {
                return new Compiled(implementationTarget.getInstrumentedType());
            }

            /**
             * A compiled handler for a visibility bridge handler. 可见性桥处理程序的已编译处理程序
             */
            @HashCodeAndEqualsPlugin.Enhance
            protected static class Compiled implements Handler.Compiled {

                /**
                 * The instrumented type. 插桩类型
                 */
                private final TypeDescription instrumentedType;

                /**
                 * Creates a new compiled handler for a visibility bridge. 为可见性桥创建新的已编译处理程序
                 *
                 * @param instrumentedType The instrumented type.
                 */
                protected Compiled(TypeDescription instrumentedType) {
                    this.instrumentedType = instrumentedType;
                }

                @Override
                public TypeWriter.MethodPool.Record assemble(MethodDescription methodDescription, MethodAttributeAppender attributeAppender, Visibility visibility) {
                    return TypeWriter.MethodPool.Record.ForDefinedMethod.OfVisibilityBridge.of(instrumentedType, methodDescription, attributeAppender);
                }
            }
        }

        /**
         * A compiled handler for implementing a method. 用于实现方法的编译处理器
         */
        interface Compiled {

            /**
             * Assembles this compiled entry with a method attribute appender. 使用方法属性附加器将此编译后的条目组装在一起
             *
             * @param methodDescription The method description to apply with this handler. 要应用于此处理程序的方法描述
             * @param attributeAppender The method attribute appender to apply together with this handler. 要与此处理程序一起应用的方法属性附加器
             * @param visibility        The represented method's minimum visibility. 表示方法的最小可见性
             * @return A method pool entry representing this handler and the given attribute appender. 表示此处理程序和给定属性附加器的方法池条目
             */
            TypeWriter.MethodPool.Record assemble(MethodDescription methodDescription, MethodAttributeAppender attributeAppender, Visibility visibility);
        }

        /**
         * A handler for a method that is implemented as byte code. 作为字节码实现的方法的处理器
         */
        @HashCodeAndEqualsPlugin.Enhance
        class ForImplementation implements Handler {

            /**
             * The implementation to apply.
             */
            private final Implementation implementation;

            /**
             * Creates a new handler for implementing a method with byte code. 创建一个新的处理程序以实现带有字节码的方法
             *
             * @param implementation The implementation to apply.
             */
            public ForImplementation(Implementation implementation) {
                this.implementation = implementation;
            }

            @Override
            public InstrumentedType prepare(InstrumentedType instrumentedType) {
                return implementation.prepare(instrumentedType);
            }

            @Override
            public Compiled compile(Implementation.Target implementationTarget) {
                return new Compiled(implementation.appender(implementationTarget));
            }

            /**
             * A compiled handler for implementing a method. 用于实现方法的编译处理程序
             */
            @HashCodeAndEqualsPlugin.Enhance
            protected static class Compiled implements Handler.Compiled {

                /**
                 * The byte code appender to apply. 要应用的字节码追加器
                 */
                private final ByteCodeAppender byteCodeAppender;

                /**
                 * Creates a new compiled handler for a method implementation. 为方法实现创建一个新的编译处理器
                 *
                 * @param byteCodeAppender The byte code appender to apply.
                 */
                protected Compiled(ByteCodeAppender byteCodeAppender) {
                    this.byteCodeAppender = byteCodeAppender;
                }

                @Override
                public TypeWriter.MethodPool.Record assemble(MethodDescription methodDescription, MethodAttributeAppender attributeAppender, Visibility visibility) {
                    return new TypeWriter.MethodPool.Record.ForDefinedMethod.WithBody(methodDescription, byteCodeAppender, attributeAppender, visibility);
                }
            }
        }

        /**
         * A handler for defining a default annotation value for a method. 用于定义方法的默认注释值的处理程序
         */
        @HashCodeAndEqualsPlugin.Enhance
        class ForAnnotationValue implements Handler, Compiled {

            /**
             * The annotation value to set as a default value.
             */
            private final AnnotationValue<?, ?> annotationValue;

            /**
             * Creates a handler for defining a default annotation value for a method.
             *
             * @param annotationValue The annotation value to set as a default value.
             */
            public ForAnnotationValue(AnnotationValue<?, ?> annotationValue) {
                this.annotationValue = annotationValue;
            }

            @Override
            public InstrumentedType prepare(InstrumentedType instrumentedType) {
                return instrumentedType;
            }

            @Override
            public Compiled compile(Implementation.Target implementationTarget) {
                return this;
            }

            @Override
            public TypeWriter.MethodPool.Record assemble(MethodDescription methodDescription, MethodAttributeAppender attributeAppender, Visibility visibility) {
                return new TypeWriter.MethodPool.Record.ForDefinedMethod.WithAnnotationDefaultValue(methodDescription, annotationValue, attributeAppender);
            }
        }
    }

    /**
     * A method registry that fully prepared the instrumented type. 完全准备插入指令的类型的方法注册表
     */
    interface Prepared {

        /**
         * Returns the fully prepared instrumented type. 返回完全准备好的检测类型
         *
         * @return The fully prepared instrumented type.
         */
        TypeDescription getInstrumentedType();

        /**
         * Returns the declared or virtually inherited methods of this type. 返回此类型的已声明或实际继承的方法
         *
         * @return The declared or virtually inherited methods of this type.
         */
        MethodList<?> getMethods();

        /**
         * Returns a list of all methods that should be instrumented.
         *
         * @return A list of all methods that should be instrumented.
         */
        MethodList<?> getInstrumentedMethods();

        /**
         * Returns the loaded type initializer of the instrumented type.
         *
         * @return The loaded type initializer of the instrumented type.
         */
        LoadedTypeInitializer getLoadedTypeInitializer();

        /**
         * The type initializer of the instrumented type.
         *
         * @return The type initializer of the instrumented type.
         */
        TypeInitializer getTypeInitializer();

        /**
         * Compiles this prepared method registry. 编译此准备好的方法注册表
         *
         * @param implementationTargetFactory A factory for creating an implementation target. 用于创建实现目标的工厂
         * @param classFileVersion            The type's class file version. 类型的类文件版本
         * @return A factory for creating an implementation target. 用于创建实现目标的工厂
         */
        Compiled compile(Implementation.Target.Factory implementationTargetFactory, ClassFileVersion classFileVersion);
    }

    /**
     * A compiled version of a method registry.
     */
    interface Compiled extends TypeWriter.MethodPool {

        /**
         * Returns the instrumented type that is to be created.
         *
         * @return The instrumented type that is to be created.
         */
        TypeDescription getInstrumentedType();

        /**
         * Returns the declared or virtually inherited methods of this type.
         *
         * @return The declared or virtually inherited methods of this type.
         */
        MethodList<?> getMethods();

        /**
         * Returns a list of all methods that should be instrumented.
         *
         * @return A list of all methods that should be instrumented.
         */
        MethodList<?> getInstrumentedMethods();

        /**
         * Returns the loaded type initializer of the instrumented type.
         *
         * @return The loaded type initializer of the instrumented type.
         */
        LoadedTypeInitializer getLoadedTypeInitializer();

        /**
         * The type initializer of the instrumented type.
         *
         * @return The type initializer of the instrumented type.
         */
        TypeInitializer getTypeInitializer();
    }

    /**
     * A default implementation of a method registry. 方法注册的默认实现
     */
    @HashCodeAndEqualsPlugin.Enhance
    class Default implements MethodRegistry {

        /**
         * The list of currently registered entries in their application order. 应用程序顺序中当前已注册条目的列表
         */
        private final List<Entry> entries; // 作用于匹配方法的操作处理

        /**
         * Creates a new default method registry without entries. 创建一个没有条目的新默认方法注册表
         */
        public Default() {
            entries = Collections.emptyList();
        }

        /**
         * Creates a new default method registry.
         *
         * @param entries The currently registered entries.
         */
        private Default(List<Entry> entries) {
            this.entries = entries;
        }

        @Override
        public MethodRegistry prepend(LatentMatcher<? super MethodDescription> matcher,
                                      Handler handler,
                                      MethodAttributeAppender.Factory attributeAppenderFactory,
                                      Transformer<MethodDescription> transformer) {
            return new Default(CompoundList.of(new Entry(matcher, handler, attributeAppenderFactory, transformer), entries));
        }

        @Override
        public MethodRegistry append(LatentMatcher<? super MethodDescription> matcher,
                                     Handler handler,
                                     MethodAttributeAppender.Factory attributeAppenderFactory,
                                     Transformer<MethodDescription> transformer) {
            return new Default(CompoundList.of(entries, new Entry(matcher, handler, attributeAppenderFactory, transformer)));
        }

        @Override
        public MethodRegistry.Prepared prepare(InstrumentedType instrumentedType,
                                               MethodGraph.Compiler methodGraphCompiler,
                                               TypeValidation typeValidation,
                                               LatentMatcher<? super MethodDescription> ignoredMethods) {
            LinkedHashMap<MethodDescription, Prepared.Entry> implementations = new LinkedHashMap<MethodDescription, Prepared.Entry>(); // 需要额外处理的新增方法，比如 MethodDelegation, 桥可见性等导致的方法增加
            Set<Handler> handlers = new HashSet<Handler>();
            MethodList<?> helperMethods = instrumentedType.getDeclaredMethods(); // 只是单纯的 插桩类 声明的方法
            for (Entry entry : entries) { // 遍历对原生方法的匹配处理项，可能delegation，可能往已存在方法中添加额外的处理等操作
                if (handlers.add(entry.getHandler())) {
                    instrumentedType = entry.getHandler().prepare(instrumentedType); // 应用于 添加一些额外的方法
                    ElementMatcher<? super MethodDescription> handledMethods = noneOf(helperMethods);
                    helperMethods = instrumentedType.getDeclaredMethods(); // 重新获取一次，该 插桩类型的 声明方法，至少会跟上 for 循环外的方法一致，可能会更多
                    for (MethodDescription helperMethod : helperMethods.filter(handledMethods)) {  // 过滤掉之前就已经存在的方法，找出新增加的方法
                        implementations.put(helperMethod, entry.asSupplementaryEntry(helperMethod));
                    }
                }
            }
            MethodGraph.Linked methodGraph = methodGraphCompiler.compile(instrumentedType); // 获取该方法所有的方法，包括继承或者实现父类（接口）的所有方法 不包括本类型的构造函数
            // Casting required for Java 6 compiler.
            ElementMatcher<? super MethodDescription> relevanceMatcher = (ElementMatcher<? super MethodDescription>) not(anyOf(implementations.keySet()))
                    .and(returns(isVisibleTo(instrumentedType)))
                    .and(hasParameters(whereNone(hasType(not(isVisibleTo(instrumentedType))))))
                    .and(ignoredMethods.resolve(instrumentedType));
            List<MethodDescription> methods = new ArrayList<MethodDescription>();
            for (MethodGraph.Node node : methodGraph.listNodes()) {
                MethodDescription methodDescription = node.getRepresentative();
                boolean visibilityBridge = instrumentedType.isPublic() && !instrumentedType.isInterface();
                if (relevanceMatcher.matches(methodDescription)) {
                    for (Entry entry : entries) {
                        if (entry.resolve(instrumentedType).matches(methodDescription)) {  // 如果 entry 的 match 匹配了对应的方法，就将该方法以及对应的实现 保存
                            implementations.put(methodDescription, entry.asPreparedEntry(instrumentedType,
                                    methodDescription,
                                    node.getMethodTypes(),
                                    node.getVisibility()));
                            visibilityBridge = false;
                            break;
                        }
                    }
                }
                if (visibilityBridge
                        && !node.getSort().isMadeVisible()
                        && methodDescription.isPublic()
                        && !(methodDescription.isAbstract() || methodDescription.isFinal())
                        && methodDescription.getDeclaringType().isPackagePrivate()) {
                    // Visibility bridges are required for public classes that inherit a public method from a package-private class. 从包私有类继承公共方法的公共类需要可见性桥
                    implementations.put(methodDescription, Prepared.Entry.forVisibilityBridge(methodDescription, node.getVisibility()));
                }
                methods.add(methodDescription);
            }
            for (MethodDescription methodDescription : CompoundList.of(
                    instrumentedType.getDeclaredMethods().filter(not(isVirtual()).and(relevanceMatcher)),
                    new MethodDescription.Latent.TypeInitializer(instrumentedType))) {  // 获取 <init> 以及 <clinit>
                for (Entry entry : entries) {
                    if (entry.resolve(instrumentedType).matches(methodDescription)) {
                        implementations.put(methodDescription, entry.asPreparedEntry(instrumentedType, methodDescription, methodDescription.getVisibility()));
                        break;
                    }
                }
                methods.add(methodDescription);
            }
            return new Prepared(implementations,
                    instrumentedType.getLoadedTypeInitializer(),
                    instrumentedType.getTypeInitializer(),
                    typeValidation.isEnabled()
                            ? instrumentedType.validated()
                            : instrumentedType,
                    methodGraph,
                    new MethodList.Explicit<MethodDescription>(methods));
        }

        /**
         * An entry of a default method registry. 默认方法注册表项
         */
        @HashCodeAndEqualsPlugin.Enhance
        protected static class Entry implements LatentMatcher<MethodDescription> {

            /**
             * The latent method matcher that this entry represents. 此项表示的潜在方法匹配器
             */
            private final LatentMatcher<? super MethodDescription> matcher;

            /**
             * The handler to apply to all matched entries. 要应用于所有匹配项的处理程序
             */
            private final Handler handler;

            /**
             * A method attribute appender factory to apply to all entries. 应用于所有项的方法属性appender工厂
             */
            private final MethodAttributeAppender.Factory attributeAppenderFactory;

            /**
             * The method transformer to be applied to implemented methods. 要应用于实现方法的方法转换器
             */
            private final Transformer<MethodDescription> transformer;

            /**
             * Creates a new entry.
             *
             * @param matcher                  The latent method matcher that this entry represents.
             * @param handler                  The handler to apply to all matched entries.
             * @param attributeAppenderFactory A method attribute appender factory to apply to all entries.
             * @param transformer              The method transformer to be applied to implemented methods.
             */
            protected Entry(LatentMatcher<? super MethodDescription> matcher,
                            Handler handler,
                            MethodAttributeAppender.Factory attributeAppenderFactory,
                            Transformer<MethodDescription> transformer) {
                this.matcher = matcher;
                this.handler = handler;
                this.attributeAppenderFactory = attributeAppenderFactory;
                this.transformer = transformer;
            }

            /**
             * Transforms this entry into a prepared state.
             *
             * @param instrumentedType  The instrumented type.
             * @param methodDescription The non-transformed method to be implemented.
             * @param visibility        The represented method's minimum visibility.
             * @return A prepared version of this entry.
             */
            protected Prepared.Entry asPreparedEntry(TypeDescription instrumentedType, MethodDescription methodDescription, Visibility visibility) {
                return asPreparedEntry(instrumentedType, methodDescription, Collections.<MethodDescription.TypeToken>emptySet(), visibility);
            }

            /**
             * Transforms this entry into a prepared state.
             *
             * @param instrumentedType  The instrumented type.
             * @param methodDescription The non-transformed method to be implemented.
             * @param methodTypes       The method types this method represents.
             * @param visibility        The represented method's minimum visibility.
             * @return A prepared version of this entry.
             */
            protected Prepared.Entry asPreparedEntry(TypeDescription instrumentedType,
                                                     MethodDescription methodDescription,
                                                     Set<MethodDescription.TypeToken> methodTypes,
                                                     Visibility visibility) {
                return new Prepared.Entry(handler,
                        attributeAppenderFactory,
                        transformer.transform(instrumentedType, methodDescription),
                        methodTypes,
                        visibility,
                        false);
            }

            /**
             * Returns a prepared entry for a supplementary method.
             *
             * @param methodDescription The method to be implemented.
             * @return An entry for a supplementary entry that is defined by a method implementation instance.
             */
            protected Prepared.Entry asSupplementaryEntry(MethodDescription methodDescription) {
                return new Prepared.Entry(handler,
                        MethodAttributeAppender.Explicit.of(methodDescription),
                        methodDescription,
                        Collections.<MethodDescription.TypeToken>emptySet(),
                        methodDescription.getVisibility(),
                        false);
            }

            /**
             * Returns this entry's handler.
             *
             * @return The handler of this entry.
             */
            protected Handler getHandler() {
                return handler;
            }

            @Override
            public ElementMatcher<? super MethodDescription> resolve(TypeDescription typeDescription) {
                return matcher.resolve(typeDescription);
            }
        }

        /**
         * A prepared version of a default method registry. 默认方法注册的准备版本
         */
        @HashCodeAndEqualsPlugin.Enhance
        protected static class Prepared implements MethodRegistry.Prepared {

            /**
             * A map of all method descriptions mapped to their handling entries. 映射到其处理项的所有方法描述的映射
             */
            private final LinkedHashMap<MethodDescription, Entry> implementations;

            /**
             * The loaded type initializer of the instrumented type. 插入指令类型的已加载类型初始值设定项
             */
            private final LoadedTypeInitializer loadedTypeInitializer;

            /**
             * The type initializer of the instrumented type. 插入指令的类型的类型初始值设定项
             */
            private final TypeInitializer typeInitializer;

            /**
             * The instrumented type.
             */
            private final TypeDescription instrumentedType;

            /**
             * A method graph describing the instrumented type. 描述插入指令的类型的方法图
             */
            private final MethodGraph.Linked methodGraph;

            /**
             * The declared or virtually inherited methods of this type. 此类型的声明或实际继承的方法
             */
            private final MethodList<?> methods;

            /**
             * Creates a prepared version of a default method registry. 创建默认方法注册表的准备版本
             *
             * @param implementations       A map of all method descriptions mapped to their handling entries.
             * @param loadedTypeInitializer The loaded type initializer of the instrumented type.
             * @param typeInitializer       The type initializer of the instrumented type.
             * @param instrumentedType      The instrumented type.
             * @param methodGraph           A method graph describing the instrumented type.
             * @param methods               The declared or virtually inherited methods of this type.
             */
            protected Prepared(LinkedHashMap<MethodDescription, Entry> implementations,
                               LoadedTypeInitializer loadedTypeInitializer,
                               TypeInitializer typeInitializer,
                               TypeDescription instrumentedType,
                               MethodGraph.Linked methodGraph,
                               MethodList<?> methods) {
                this.implementations = implementations;
                this.loadedTypeInitializer = loadedTypeInitializer;
                this.typeInitializer = typeInitializer;
                this.instrumentedType = instrumentedType;
                this.methodGraph = methodGraph;
                this.methods = methods;
            }

            @Override
            public TypeDescription getInstrumentedType() {
                return instrumentedType;
            }

            @Override
            public LoadedTypeInitializer getLoadedTypeInitializer() {
                return loadedTypeInitializer;
            }

            @Override
            public TypeInitializer getTypeInitializer() {
                return typeInitializer;
            }

            @Override
            public MethodList<?> getMethods() {
                return methods;
            }

            @Override
            public MethodList<?> getInstrumentedMethods() {
                return new MethodList.Explicit<MethodDescription>(new ArrayList<MethodDescription>(implementations.keySet())).filter(not(isTypeInitializer()));
            }

            @Override
            public MethodRegistry.Compiled compile(Implementation.Target.Factory implementationTargetFactory, ClassFileVersion classFileVersion) {
                Map<Handler, Handler.Compiled> compilationCache = new HashMap<Handler, Handler.Compiled>();
                Map<MethodAttributeAppender.Factory, MethodAttributeAppender> attributeAppenderCache = new HashMap<MethodAttributeAppender.Factory, MethodAttributeAppender>();
                LinkedHashMap<MethodDescription, Compiled.Entry> entries = new LinkedHashMap<MethodDescription, Compiled.Entry>();
                Implementation.Target implementationTarget = implementationTargetFactory.make(instrumentedType, methodGraph, classFileVersion);
                for (Map.Entry<MethodDescription, Entry> entry : implementations.entrySet()) { // 遍历需要重新实现的 implementations
                    Handler.Compiled cachedHandler = compilationCache.get(entry.getValue().getHandler()); // 获取 重新组装的 各种实现 比如 MethodCall appender 之类的，而最终的实现都是靠 ByteCodeAppender 托底
                    if (cachedHandler == null) {
                        cachedHandler = entry.getValue().getHandler().compile(implementationTarget);
                        compilationCache.put(entry.getValue().getHandler(), cachedHandler);
                    }
                    MethodAttributeAppender cachedAttributeAppender = attributeAppenderCache.get(entry.getValue().getAppenderFactory());
                    if (cachedAttributeAppender == null) {
                        cachedAttributeAppender = entry.getValue().getAppenderFactory().make(instrumentedType);
                        attributeAppenderCache.put(entry.getValue().getAppenderFactory(), cachedAttributeAppender);
                    }
                    entries.put(entry.getKey(), new Compiled.Entry(cachedHandler,
                            cachedAttributeAppender,
                            entry.getValue().getMethodDescription(),
                            entry.getValue().resolveBridgeTypes(),
                            entry.getValue().getVisibility(),
                            entry.getValue().isBridgeMethod()));
                }
                return new Compiled(instrumentedType,
                        loadedTypeInitializer,
                        typeInitializer,
                        methods,
                        entries,
                        classFileVersion.isAtLeast(ClassFileVersion.JAVA_V5));
            }

            /**
             * An entry of a prepared method registry. 准备好的方法注册项
             */
            @HashCodeAndEqualsPlugin.Enhance
            protected static class Entry {

                /**
                 * The handler for implementing methods. 实现方法的处理程序
                 */
                private final Handler handler;

                /**
                 * A attribute appender factory for appending attributes for any implemented method. 属性附加器工厂，用于为任何实现的方法附加属性
                 */
                private final MethodAttributeAppender.Factory attributeAppenderFactory;

                /**
                 * The method this entry represents. 此项表示的方法
                 */
                private final MethodDescription methodDescription;

                /**
                 * The method's type tokens. 方法的类型标记
                 */
                private final Set<MethodDescription.TypeToken> typeTokens;

                /**
                 * The minimum required visibility of this method. 此方法所需的最低可见性
                 */
                private Visibility visibility;

                /**
                 * Is {@code true} if this entry represents a bridge method.
                 */
                private final boolean bridgeMethod;

                /**
                 * Creates a new prepared entry.
                 *
                 * @param handler                  The handler for implementing methods.
                 * @param attributeAppenderFactory A attribute appender factory for appending attributes for any implemented method.
                 * @param methodDescription        The method this entry represents.
                 * @param typeTokens               A set of bridges representing the bridge methods of this method.
                 * @param visibility               The minimum required visibility of this method.
                 * @param bridgeMethod             {@code true} if this entry represents a bridge method.
                 */
                protected Entry(Handler handler,
                                MethodAttributeAppender.Factory attributeAppenderFactory,
                                MethodDescription methodDescription,
                                Set<MethodDescription.TypeToken> typeTokens,
                                Visibility visibility,
                                boolean bridgeMethod) {
                    this.handler = handler;
                    this.attributeAppenderFactory = attributeAppenderFactory;
                    this.methodDescription = methodDescription;
                    this.typeTokens = typeTokens;
                    this.visibility = visibility;
                    this.bridgeMethod = bridgeMethod;
                }

                /**
                 * Creates an entry for a visibility bridge.
                 *
                 * @param bridgeTarget The bridge method's target.
                 * @param visibility   The represented method's minimum visibility.
                 * @return An entry representing a visibility bridge.
                 */
                protected static Entry forVisibilityBridge(MethodDescription bridgeTarget, Visibility visibility) {
                    return new Entry(Handler.ForVisibilityBridge.INSTANCE,
                            MethodAttributeAppender.Explicit.of(bridgeTarget),
                            bridgeTarget,
                            Collections.<MethodDescription.TypeToken>emptySet(),
                            visibility,
                            true);
                }

                /**
                 * Returns this entry's handler.
                 *
                 * @return The entry's handler.
                 */
                protected Handler getHandler() {
                    return handler;
                }

                /**
                 * Returns this entry's attribute appender factory.
                 *
                 * @return This entry's attribute appender factory.
                 */
                protected MethodAttributeAppender.Factory getAppenderFactory() {
                    return attributeAppenderFactory;
                }

                /**
                 * Returns the method description this entry represents.
                 *
                 * @return The method description this entry represents.
                 */
                protected MethodDescription getMethodDescription() {
                    return methodDescription;
                }

                /**
                 * Resolves the type tokens of all bridge methods that are required to be implemented for this entry.
                 *
                 * @return A set of type tokens representing the bridge methods required for implementing this type.
                 */
                protected Set<MethodDescription.TypeToken> resolveBridgeTypes() {
                    HashSet<MethodDescription.TypeToken> typeTokens = new HashSet<MethodDescription.TypeToken>(this.typeTokens);
                    typeTokens.remove(methodDescription.asTypeToken());
                    return typeTokens;
                }

                /**
                 * Returns the represented method's minimum visibility.
                 *
                 * @return The represented method's minimum visibility.
                 */
                protected Visibility getVisibility() {
                    return visibility;
                }

                /**
                 * Returns {@code true} if this entry represents a bridge method.
                 *
                 * @return {@code true} if this entry represents a bridge method.
                 */
                protected boolean isBridgeMethod() {
                    return bridgeMethod;
                }
            }
        }

        /**
         * A compiled version of a default method registry. 默认方法注册表的编译版本
         */
        @HashCodeAndEqualsPlugin.Enhance
        protected static class Compiled implements MethodRegistry.Compiled {

            /**
             * The instrumented type. 插桩类型
             */
            private final TypeDescription instrumentedType;

            /**
             * The loaded type initializer of the instrumented type. 插桩类型的已加载类型初始值设定项
             */
            private final LoadedTypeInitializer loadedTypeInitializer;

            /**
             * The type initializer of the instrumented type. 插桩类型的类型初始值设定项
             */
            private final TypeInitializer typeInitializer;

            /**
             * The declared or virtually inherited methods of this type. 此类型的声明或实际继承的方法
             */
            private final MethodList<?> methods;

            /**
             * A map of all method descriptions mapped to their handling entries. 映射到其处理项的所有方法描述的映射
             */
            private final LinkedHashMap<MethodDescription, Entry> implementations;

            /**
             * {@code true} if the created type supports bridge methods. {@code true} 如果创建的类型支持桥方法
             */
            private final boolean supportsBridges;

            /**
             * Creates a new compiled version of a default method registry. 创建默认方法注册表的新编译版本
             *
             * @param instrumentedType      The instrumented type.
             * @param loadedTypeInitializer The loaded type initializer of the instrumented type.
             * @param typeInitializer       The type initializer of the instrumented type.
             * @param methods               The declared or virtually inherited methods of this type.
             * @param implementations       A map of all method descriptions mapped to their handling entries. 所有方法描述的映射，映射到它们的处理条目
             * @param supportsBridges       {@code true} if the created type supports bridge methods. {@code true} 如果创建的类型支持桥方法
             */
            protected Compiled(TypeDescription instrumentedType,
                               LoadedTypeInitializer loadedTypeInitializer,
                               TypeInitializer typeInitializer,
                               MethodList<?> methods,
                               LinkedHashMap<MethodDescription, Entry> implementations,
                               boolean supportsBridges) {
                this.instrumentedType = instrumentedType;
                this.loadedTypeInitializer = loadedTypeInitializer;
                this.typeInitializer = typeInitializer;
                this.methods = methods;
                this.implementations = implementations;
                this.supportsBridges = supportsBridges;
            }

            @Override
            public TypeDescription getInstrumentedType() {
                return instrumentedType;
            }

            @Override
            public LoadedTypeInitializer getLoadedTypeInitializer() {
                return loadedTypeInitializer;
            }

            @Override
            public TypeInitializer getTypeInitializer() {
                return typeInitializer;
            }

            @Override
            public MethodList<?> getMethods() {
                return methods;
            }

            @Override
            public MethodList<?> getInstrumentedMethods() {
                return new MethodList.Explicit<MethodDescription>(new ArrayList<MethodDescription>(implementations.keySet())).filter(not(isTypeInitializer()));
            }

            @Override
            public Record target(MethodDescription methodDescription) {
                Entry entry = implementations.get(methodDescription);
                return entry == null
                        ? new Record.ForNonImplementedMethod(methodDescription) // 如果没有在 implementations 缓存中找到相关的实现项，则意味着不需要进行额外增强
                        : entry.bind(instrumentedType, supportsBridges); // 组装相应的方法实现格式
            }

            /**
             * An entry of a compiled method registry. 已编译方法注册的一个条目
             */
            @HashCodeAndEqualsPlugin.Enhance
            protected static class Entry {

                /**
                 * The handler to be used for implementing a method. 用于实现一个方法的处理器
                 */
                private final Handler.Compiled handler;

                /**
                 * The attribute appender of a compiled method. 编译方法的属性附加器
                 */
                private final MethodAttributeAppender attributeAppender;

                /**
                 * The method to be implemented including potential transformations.
                 */
                private final MethodDescription methodDescription;

                /**
                 * The type tokens representing all bridge methods for the method.
                 */
                private final Set<MethodDescription.TypeToken> bridgeTypes;

                /**
                 * The represented method's minimum visibility.
                 */
                private final Visibility visibility;

                /**
                 * {@code true} if this entry represents a bridge method.
                 */
                private final boolean bridgeMethod;

                /**
                 * Creates a new entry for a compiled method registry.
                 *
                 * @param handler           The handler to be used for implementing a method.
                 * @param attributeAppender The attribute appender of a compiled method.
                 * @param methodDescription The method to be implemented including potential transformations.
                 * @param bridgeTypes       The type tokens representing all bridge methods for the method.
                 * @param visibility        The represented method's minimum visibility.
                 * @param bridgeMethod      {@code true} if this entry represents a bridge method.
                 */
                protected Entry(Handler.Compiled handler,
                                MethodAttributeAppender attributeAppender,
                                MethodDescription methodDescription,
                                Set<MethodDescription.TypeToken> bridgeTypes,
                                Visibility visibility,
                                boolean bridgeMethod) {
                    this.handler = handler;
                    this.attributeAppender = attributeAppender;
                    this.methodDescription = methodDescription;
                    this.bridgeTypes = bridgeTypes;
                    this.visibility = visibility;
                    this.bridgeMethod = bridgeMethod;
                }

                /**
                 * Transforms this entry into a method record. 将此项转换为方法记录
                 *
                 * @param instrumentedType The instrumented type to bind.
                 * @param supportsBridges  {@code true} if the record should support bridge methods.
                 * @return A record representing this entry's properties.
                 */
                protected Record bind(TypeDescription instrumentedType, boolean supportsBridges) {
                    if (bridgeMethod && !supportsBridges) {
                        return new Record.ForNonImplementedMethod(methodDescription);
                    }
                    Record record = handler.assemble(methodDescription, attributeAppender, visibility); // 将涉及到的方法字节码都绑定在一起，比如本身的方法描述，方法属性，方法可见行之类的实例数据
                    return supportsBridges
                            ? TypeWriter.MethodPool.Record.AccessBridgeWrapper.of(record, instrumentedType, methodDescription, bridgeTypes, attributeAppender)
                            : record;
                }
            }
        }
    }
}
