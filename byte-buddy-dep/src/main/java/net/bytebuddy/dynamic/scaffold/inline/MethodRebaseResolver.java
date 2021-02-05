package net.bytebuddy.dynamic.scaffold.inline;

import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodAccessorFactory;
import net.bytebuddy.implementation.auxiliary.AuxiliaryType;
import net.bytebuddy.implementation.auxiliary.TrivialType;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.constant.NullConstant;
import net.bytebuddy.utility.CompoundList;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.is;

/**
 * A method rebase resolver is responsible for mapping methods of an instrumented type to an alternative signature.
 * This way a method can exist in two versions within a class: 方法 rebase解析器 负责将检测类型的方法映射到备用签名。这样，一个方法可以在一个类中以两个版本存在
 * <ol>
 * <li>The rebased method which represents the original implementation as it is present in a class file.</li> 在类文件中表示原始实现的rebased方法
 * <li>An overridden method which implements user code which is still able to invoke the original, rebased method.</li> 一个重写的方法，它实现了用户代码，用户代码仍然能够调用原始的、重定基的方法
 * </ol>
 */
public interface MethodRebaseResolver {

    /**
     * Checks if a method is eligible for rebasing and resolves this possibly rebased method. 检查某个方法是否有资格重新调整，并解决此可能已重新调整的方法
     *
     * @param methodDescription A description of the method to resolve. 解析方法的描述
     * @return A resolution for the given method. 给定方法的解析
     */
    Resolution resolve(MethodDescription.InDefinedShape methodDescription);

    /**
     * Returns a (potentially empty) list of auxiliary types that are required by this method rebase resolver. 返回此方法 rebase resolver 所需的辅助类型（可能为空）列表
     *
     * @return A list of auxiliary types that are required by this method rebase resolver. 此方法所需的辅助类型的列表重新设置解析器的基础
     */
    List<DynamicType> getAuxiliaryTypes();

    /**
     * Returns a map of all rebasable methods' signature tokens to their resolution. 返回所有可再利用方法的签名标记到其解析的映射
     *
     * @return A map of all rebasable methods' signature tokens to their resolution.
     */
    Map<MethodDescription.SignatureToken, Resolution> asTokenMap();

    /**
     * A method rebase resolver that preserves any method in its original form. 将任何方法保留为其原始形式的方法重新基分解器
     */
    enum Disabled implements MethodRebaseResolver {

        /**
         * The singleton instance.
         */
        INSTANCE;

        @Override
        public Resolution resolve(MethodDescription.InDefinedShape methodDescription) {
            return new Resolution.Preserved(methodDescription);
        }

        @Override
        public List<DynamicType> getAuxiliaryTypes() {
            return Collections.emptyList();
        }

        @Override
        public Map<MethodDescription.SignatureToken, Resolution> asTokenMap() {
            return Collections.emptyMap();
        }

    }

    /**
     * A resolution for a method that was checked by a {@link MethodRebaseResolver}. 由{@link MethodRebaseResolver}检查的方法的解析
     */
    interface Resolution {

        /**
         * Checks if this resolution represents a rebased method.
         *
         * @return {@code true} if this resolution requires to rebase a method.
         */
        boolean isRebased();

        /**
         * Returns the resolved method if this resolution represents a rebased method or the original method.
         *
         * @return The resolved method if this resolution represents a rebased method or the original method.
         */
        MethodDescription.InDefinedShape getResolvedMethod();

        /**
         * A rebased method might require additional arguments in order to create a distinct signature. The
         * stack manipulation that is returned from this method loads these arguments onto the operand stack. For
         * a non-rebased method, this method throws an {@link java.lang.IllegalArgumentException}.
         *
         * @return A stack manipulation that loaded the additional arguments onto the stack, if any. 将附加参数加载到堆栈（如果有的话）的堆栈操作
         */
        StackManipulation getAdditionalArguments();

        /**
         * A {@link MethodRebaseResolver.Resolution} of a non-rebased method.
         */
        @HashCodeAndEqualsPlugin.Enhance
        class Preserved implements Resolution {

            /**
             * The preserved method.
             */
            private final MethodDescription.InDefinedShape methodDescription;

            /**
             * Creates a new {@link MethodRebaseResolver.Resolution} for
             * a non-rebased method.
             *
             * @param methodDescription The preserved method.
             */
            public Preserved(MethodDescription.InDefinedShape methodDescription) {
                this.methodDescription = methodDescription;
            }

            @Override
            public boolean isRebased() {
                return false;
            }

            @Override
            public MethodDescription.InDefinedShape getResolvedMethod() {
                return methodDescription;
            }

            @Override
            public StackManipulation getAdditionalArguments() {
                throw new IllegalStateException("Cannot process additional arguments for non-rebased method: " + methodDescription);
            }
        }

        /**
         * A {@link MethodRebaseResolver.Resolution} of a rebased method.
         */
        @HashCodeAndEqualsPlugin.Enhance
        class ForRebasedMethod implements Resolution {

            /**
             * The rebased method.
             */
            private final MethodDescription.InDefinedShape methodDescription;

            /**
             * Creates a resolution for a rebased method.
             *
             * @param methodDescription The rebased method.
             */
            protected ForRebasedMethod(MethodDescription.InDefinedShape methodDescription) {
                this.methodDescription = methodDescription;
            }

            /**
             * Resolves a rebasement for the provided method.
             *
             * @param instrumentedType      The instrumented type.
             * @param methodDescription     The method to be rebased.
             * @param methodNameTransformer The transformer to use for renaming the method.
             * @return A resolution for rebasing the provided method.
             */
            public static Resolution of(TypeDescription instrumentedType,
                                        MethodDescription.InDefinedShape methodDescription,
                                        MethodNameTransformer methodNameTransformer) {
                return new ForRebasedMethod(new RebasedMethod(instrumentedType, methodDescription, methodNameTransformer));
            }

            @Override
            public boolean isRebased() {
                return true;
            }

            @Override
            public MethodDescription.InDefinedShape getResolvedMethod() {
                return methodDescription;
            }

            @Override
            public StackManipulation getAdditionalArguments() {
                return StackManipulation.Trivial.INSTANCE;
            }

            /**
             * A description of a rebased method.
             */
            protected static class RebasedMethod extends MethodDescription.InDefinedShape.AbstractBase {

                /**
                 * The instrumented type.
                 */
                private final TypeDescription instrumentedType;

                /**
                 * The method that is being rebased.
                 */
                private final InDefinedShape methodDescription;

                /**
                 * The transformer to use for renaming the method.
                 */
                private final MethodNameTransformer methodNameTransformer;

                /**
                 * Creates a new rebased method.
                 *
                 * @param instrumentedType      The instrumented type.
                 * @param methodDescription     The method that is being rebased.
                 * @param methodNameTransformer The transformer to use for renaming the method.
                 */
                protected RebasedMethod(TypeDescription instrumentedType, InDefinedShape methodDescription, MethodNameTransformer methodNameTransformer) {
                    this.instrumentedType = instrumentedType;
                    this.methodDescription = methodDescription;
                    this.methodNameTransformer = methodNameTransformer;
                }

                @Override
                public TypeDescription.Generic getReturnType() {
                    return methodDescription.getReturnType().asRawType();
                }

                @Override
                public ParameterList<ParameterDescription.InDefinedShape> getParameters() {
                    return new ParameterList.Explicit.ForTypes(this, methodDescription.getParameters().asTypeList().asRawTypes());
                }

                @Override
                public TypeList.Generic getExceptionTypes() {
                    return methodDescription.getExceptionTypes().asRawTypes();
                }

                @Override
                public AnnotationValue<?, ?> getDefaultValue() {
                    return AnnotationValue.UNDEFINED;
                }

                @Override
                public TypeList.Generic getTypeVariables() {
                    return new TypeList.Generic.Empty();
                }

                @Override
                public AnnotationList getDeclaredAnnotations() {
                    return new AnnotationList.Empty();
                }

                @Override
                public TypeDescription getDeclaringType() {
                    return methodDescription.getDeclaringType();
                }

                @Override
                public int getModifiers() {
                    return Opcodes.ACC_SYNTHETIC
                            | (methodDescription.isStatic() ? Opcodes.ACC_STATIC : EMPTY_MASK)
                            | (methodDescription.isNative() ? Opcodes.ACC_NATIVE : EMPTY_MASK)
                            | (instrumentedType.isInterface() ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE);
                }

                @Override
                public String getInternalName() {
                    return methodNameTransformer.transform(methodDescription);
                }
            }
        }

        /**
         * A {@link MethodRebaseResolver.Resolution} of a rebased constructor.
         */
        @HashCodeAndEqualsPlugin.Enhance
        class ForRebasedConstructor implements Resolution {

            /**
             * The rebased constructor.
             */
            private final MethodDescription.InDefinedShape methodDescription;

            /**
             * Creates a new resolution for a rebased constructor.
             *
             * @param methodDescription The rebased constructor.
             */
            protected ForRebasedConstructor(MethodDescription.InDefinedShape methodDescription) {
                this.methodDescription = methodDescription;
            }

            /**
             * Resolves a constructor rebasement.
             *
             * @param methodDescription The constructor to rebase.
             * @param placeholderType   The placeholder type to use to distinguish the constructor's signature.
             * @return A resolution of the provided constructor.
             */
            public static Resolution of(MethodDescription.InDefinedShape methodDescription, TypeDescription placeholderType) {
                return new ForRebasedConstructor(new RebasedConstructor(methodDescription, placeholderType));
            }

            @Override
            public boolean isRebased() {
                return true;
            }

            @Override
            public MethodDescription.InDefinedShape getResolvedMethod() {
                return methodDescription;
            }

            @Override
            public StackManipulation getAdditionalArguments() {
                return NullConstant.INSTANCE;
            }

            /**
             * An description of a rebased constructor.
             */
            protected static class RebasedConstructor extends MethodDescription.InDefinedShape.AbstractBase {

                /**
                 * The constructor that is rebased.
                 */
                private final InDefinedShape methodDescription;

                /**
                 * The placeholder type that is used to distinguish the constructor's signature.
                 */
                private final TypeDescription placeholderType;

                /**
                 * Creates a new rebased constructor.
                 *
                 * @param methodDescription The constructor that is rebased.
                 * @param placeholderType   The placeholder type that is used to distinguish the constructor's signature.
                 */
                protected RebasedConstructor(InDefinedShape methodDescription, TypeDescription placeholderType) {
                    this.methodDescription = methodDescription;
                    this.placeholderType = placeholderType;
                }

                @Override
                public TypeDescription.Generic getReturnType() {
                    return TypeDescription.Generic.VOID;
                }

                @Override
                public ParameterList<ParameterDescription.InDefinedShape> getParameters() {
                    return new ParameterList.Explicit.ForTypes(this, CompoundList.of(methodDescription.getParameters().asTypeList().asErasures(), placeholderType));
                }

                @Override
                public TypeList.Generic getExceptionTypes() {
                    return methodDescription.getExceptionTypes().asRawTypes();
                }

                @Override
                public AnnotationValue<?, ?> getDefaultValue() {
                    return AnnotationValue.UNDEFINED;
                }

                @Override
                public TypeList.Generic getTypeVariables() {
                    return new TypeList.Generic.Empty();
                }

                @Override
                public AnnotationList getDeclaredAnnotations() {
                    return new AnnotationList.Empty();
                }

                @Override
                public TypeDescription getDeclaringType() {
                    return methodDescription.getDeclaringType();
                }

                @Override
                public int getModifiers() {
                    return Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PRIVATE;
                }

                @Override
                public String getInternalName() {
                    return MethodDescription.CONSTRUCTOR_INTERNAL_NAME;
                }
            }
        }
    }

    /**
     * A default implementation of a method rebase resolver. 一种默认的方法实现
     */
    @HashCodeAndEqualsPlugin.Enhance
    class Default implements MethodRebaseResolver {

        /**
         * A mapping of rebased methods to their existing resolutions.
         */
        private final Map<MethodDescription.InDefinedShape, Resolution> resolutions;

        /**
         * A list of dynamic types that need to be appended to the created type in order to allow for the rebasement.
         */
        private final List<DynamicType> dynamicTypes;

        /**
         * Creates a new default method rebased resolver.
         *
         * @param resolutions  A mapping of rebased methods to their existing resolutions.
         * @param dynamicTypes A list of dynamic types that need to be appended to the created type in order to allow for the rebasement.
         */
        protected Default(Map<MethodDescription.InDefinedShape, Resolution> resolutions, List<DynamicType> dynamicTypes) {
            this.resolutions = resolutions;
            this.dynamicTypes = dynamicTypes;
        }

        /**
         * Creates a new method rebase resolver. 此方法主要是对需要进行 rebase 的方法进行预处理，如何处理？对需要进行 rebase 对方法保留，只不过，需要在之前方法进行重命名
         *
         * @param instrumentedType            The instrumented type.
         * @param rebaseableMethodTokens      Tokens describing all methods that can possibly be rebased. 描述所有可能被重定基的方法的标记
         * @param classFileVersion            The class file version for the instrumentation.
         * @param auxiliaryTypeNamingStrategy The naming strategy for naming a potential auxiliary type.
         * @param methodNameTransformer       A transformer for method names.
         * @return A method rebase resolver that is capable of rebasing any of the provided methods.
         */
        public static MethodRebaseResolver make(TypeDescription instrumentedType,
                                                Set<? extends MethodDescription.Token> rebaseableMethodTokens,
                                                ClassFileVersion classFileVersion,
                                                AuxiliaryType.NamingStrategy auxiliaryTypeNamingStrategy,
                                                MethodNameTransformer methodNameTransformer) {
            DynamicType placeholderType = null;
            Map<MethodDescription.InDefinedShape, Resolution> resolutions = new HashMap<MethodDescription.InDefinedShape, Resolution>();
            for (MethodDescription.InDefinedShape instrumentedMethod : instrumentedType.getDeclaredMethods()) {
                if (rebaseableMethodTokens.contains(instrumentedMethod.asToken(is(instrumentedType)))) {
                    Resolution resolution;
                    if (instrumentedMethod.isConstructor()) {
                        if (placeholderType == null) {
                            placeholderType = TrivialType.SIGNATURE_RELEVANT.make(auxiliaryTypeNamingStrategy.name(instrumentedType),
                                    classFileVersion,
                                    MethodAccessorFactory.Illegal.INSTANCE);
                        }
                        resolution = Resolution.ForRebasedConstructor.of(instrumentedMethod, placeholderType.getTypeDescription());
                    } else {
                        resolution = Resolution.ForRebasedMethod.of(instrumentedType, instrumentedMethod, methodNameTransformer);
                    }
                    resolutions.put(instrumentedMethod, resolution);
                }
            }
            return placeholderType == null
                    ? new Default(resolutions, Collections.<DynamicType>emptyList())
                    : new Default(resolutions, Collections.singletonList(placeholderType));
        }

        @Override
        public Resolution resolve(MethodDescription.InDefinedShape methodDescription) {
            Resolution resolution = resolutions.get(methodDescription);
            return resolution == null
                    ? new Resolution.Preserved(methodDescription)
                    : resolution;
        }

        @Override
        public List<DynamicType> getAuxiliaryTypes() {
            return dynamicTypes;
        }

        @Override
        public Map<MethodDescription.SignatureToken, Resolution> asTokenMap() {
            Map<MethodDescription.SignatureToken, Resolution> tokenMap = new HashMap<MethodDescription.SignatureToken, Resolution>();
            for (Map.Entry<MethodDescription.InDefinedShape, Resolution> entry : resolutions.entrySet()) {
                tokenMap.put(entry.getKey().asSignatureToken(), entry.getValue());
            }
            return tokenMap;
        }
    }
}
