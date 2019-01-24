package com.phelat.livedatadsl.processor

import com.phelat.livedatadsl.annotation.LiveDataDSL
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import java.io.IOException
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.tools.Diagnostic

class Processor : AbstractProcessor() {

    private lateinit var filer: Filer
    private lateinit var messager: Messager
    private lateinit var elementUtils: Elements

    @Synchronized
    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        filer = processingEnv.filer
        messager = processingEnv.messager
        elementUtils = processingEnv.elementUtils
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnvironment: RoundEnvironment
    ): Boolean {
        if (!roundEnvironment.processingOver()) {
            for (typeElement in getTypeElements(roundEnvironment.rootElements, annotations)) {
                processTypeElement(typeElement)
            }
        }
        return true
    }

    private fun processTypeElement(typeElement: TypeElement) {
        val packageName = elementUtils.getPackageOf(typeElement).qualifiedName.toString()
        val typeName = typeElement.simpleName.toString()
        val className = ClassName.get(packageName, typeName)
        val generatedClass = ClassName.get(packageName, "${typeName}_DSL")

        val classBuilder = TypeSpec.classBuilder(generatedClass).apply {
            addModifiers(Modifier.PUBLIC)
            superclass(className)
            generateBodyOfClass(typeElement, this)
        }

        try {
            JavaFile.builder(packageName, classBuilder.build())
                .build()
                .writeTo(filer)
        } catch (e: IOException) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.toString(), typeElement)
        }
    }

    private fun generateBodyOfClass(typeElement: TypeElement, classBuilder: TypeSpec.Builder) {
        val enclosedElements = typeElement.enclosedElements
        enclosedElements.forEach { element ->
            if (element.kind == ElementKind.CONSTRUCTOR) {
                generateConstructor(element, classBuilder)
            } else if (element.kind == ElementKind.FIELD) {
                generateDSLs(element, classBuilder)
            }
        }
    }

    private fun generateConstructor(element: Element, classBuilder: TypeSpec.Builder) {
        val constructor = element as ExecutableElement
        val constructorParameters = constructor.parameters

        val constructorBuilder = MethodSpec.constructorBuilder().apply {
            addModifiers(constructor.modifiers)
            val superCall = StringBuilder("super(")
            constructorParameters.forEachIndexed { index, variableElement ->
                addParameter(
                    TypeName.get(variableElement.asType()),
                    variableElement.simpleName.toString(),
                    *variableElement.modifiers.toTypedArray()
                )
                superCall.append(variableElement.simpleName.toString())
                if (index < constructorParameters.size - 1) {
                    superCall.append(", ")
                }
            }
            superCall.append(");")
            addCode(superCall.toString())
        }
        classBuilder.addMethod(constructorBuilder.build())
    }

    private fun generateDSLs(element: Element, classBuilder: TypeSpec.Builder) {
        val variable = element as VariableElement
        val variableName = variable.simpleName.toString()
        val liveDataAnnotation = variable.getAnnotation(LiveDataDSL::class.java)
        if (liveDataAnnotation != null) {
            if (variable.asType() is DeclaredType) {
                val declaredType = variable.asType() as DeclaredType
                val mutableLiveData = getMutableLiveData(declaredType)
                val mutableLiveDataGenerics = mutableLiveData.typeArguments
                val observerGenerics = generateObserverGenerics(mutableLiveDataGenerics)
                val liveDataGenerics = getGenericTypeNames(mutableLiveDataGenerics)
                val functionOneGenerics = generateFunctionOneGenerics(observerGenerics)

                val liveDataFieldName = "get${variableName.substring(0, 1)
                    .toUpperCase()}${variableName.substring(1)}"
                val observer = ClassName.get(getLifeCyclePackage(), "Observer")

                val rawLiveData = ClassName.get(getLifeCyclePackage(), "LiveData")
                val liveData = ParameterizedTypeName.get(
                    rawLiveData,
                    *liveDataGenerics.toTypedArray()
                )

                val rawFunctionResult = ClassName.get(
                    "com.phelat.livedatadsl.model",
                    "FunctionResult"
                )

                val liveDataObservationCode = generateLiveDataObservationCode(
                    liveDataFieldName,
                    observerGenerics,
                    observer,
                    rawFunctionResult
                )
                val liveDataObserveForEverCode = generateLiveDataObserveForEverCode(
                    liveDataFieldName,
                    observerGenerics,
                    observer,
                    rawFunctionResult
                )

                generateDSLFunction(
                    variableName,
                    functionOneGenerics,
                    liveData,
                    liveDataObservationCode,
                    liveDataObserveForEverCode,
                    liveDataGenerics,
                    observer,
                    rawFunctionResult,
                    classBuilder
                )
            }
        }
    }

    private fun generateObserverGenerics(typeArguments: List<TypeMirror>): String {
        val generics = StringBuilder()
        typeArguments.forEachIndexed { index, typeMirror ->
            generics.append(typeMirror.toString())
            if (index < typeArguments.size - 1) {
                generics.append(", ")
            }
        }
        return generics.toString()
    }

    private fun getGenericTypeNames(typeArguments: List<TypeMirror>): List<TypeName> {
        val generics = mutableListOf<TypeName>()
        typeArguments.forEach { typeMirror ->
            val declaredType = typeMirror as DeclaredType
            val typeElement = declaredType.asElement() as TypeElement
            val packageName = elementUtils.getPackageOf(typeElement).qualifiedName
            val typeName = declaredType.asElement().simpleName
            val genericClassName = ClassName.get(packageName.toString(), typeName.toString())
            generics.add(genericClassName.box())
        }
        return generics
    }

    private fun getMutableLiveData(declaredType: DeclaredType): DeclaredType {
        val typeElement = declaredType.asElement() as TypeElement
        val packageName = elementUtils.getPackageOf(typeElement).qualifiedName
        val variableTypeName = declaredType.asElement().simpleName
        val isPackageArch = packageName.contentEquals(getLifeCyclePackage())
        val isClassLiveData = variableTypeName.contentEquals("MutableLiveData")
        return if (isPackageArch && isClassLiveData) {
            declaredType
        } else {
            getMutableLiveData(typeElement.superclass as DeclaredType)
        }
    }

    private fun generateLiveDataObservationCode(
        liveDataFieldName: String,
        observerGenerics: String,
        observer: ClassName,
        rawFunctionResult: ClassName
    ): CodeBlock {
        return CodeBlock.builder()
            .add(
                "\$T observer = new \$T<$observerGenerics>(){" +
                        "@Override public void onChanged($observerGenerics param) {function.invoke(param);}" +
                        "};",
                observer, observer
            )
            .add(
                "$liveDataFieldName().observe(lifecycleOwner, observer);"
            )
            .add("return new \$T(observer, $liveDataFieldName());", rawFunctionResult)
            .build()
    }

    private fun generateLiveDataObserveForEverCode(
        liveDataFieldName: String,
        observerGenerics: String,
        observer: ClassName,
        rawFunctionResult: ClassName
    ): CodeBlock {
        return CodeBlock.builder()
            .add(
                "\$T observer = new \$T<$observerGenerics>(){" +
                        "@Override public void onChanged($observerGenerics param) {function.invoke(param);}" +
                        "};",
                observer, observer
            )
            .add(
                "$liveDataFieldName().observeForever(observer);"
            )
            .add("return new \$T(observer, $liveDataFieldName());", rawFunctionResult)
            .build()
    }

    private fun generateFunctionOneGenerics(observerGenerics: String): String {
        val generics = StringBuilder()
        if (observerGenerics.isEmpty()) {
            generics.append("kotlin.Unit, kotlin.Unit")
        } else {
            generics.append("$observerGenerics, kotlin.Unit")
        }
        return generics.toString()
    }

    private fun generateDSLFunction(
        variableName: String,
        functionOneGenerics: String,
        liveData: ParameterizedTypeName,
        liveDataObservationCode: CodeBlock,
        liveDataObserveForEverCode: CodeBlock,
        observerGenerics: List<TypeName>,
        rawObserver: ClassName,
        rawFunctionResult: ClassName,
        classBuilder: TypeSpec.Builder
    ) {
        val functionOne = ClassName.get("kotlin.jvm.functions", "Function1")
        val lifecycleOwner = ClassName.get(getLifeCyclePackage(), "LifecycleOwner")

        val observer = ParameterizedTypeName.get(rawObserver, *observerGenerics.toTypedArray())
        val functionResult = ParameterizedTypeName.get(rawFunctionResult, observer.box(), liveData.box())

        val dslWithLifeCycle = MethodSpec.methodBuilder(variableName).apply {
            addModifiers(Modifier.PUBLIC)
            returns(functionResult)
            addParameter(lifecycleOwner, "lifecycleOwner")
            addParameter(
                TypeVariableName.get("final ${functionOne.packageName()}.${functionOne.simpleName()}<$functionOneGenerics>"),
                "function"
            )
            addCode(liveDataObservationCode)
        }

        val dslWithoutLifeCycle = MethodSpec.methodBuilder(variableName).apply {
            addModifiers(Modifier.PUBLIC)
            returns(functionResult)
            addParameter(
                TypeVariableName.get("final ${functionOne.packageName()}.${functionOne.simpleName()}<$functionOneGenerics>"),
                "function"
            )
            addCode(liveDataObserveForEverCode)
        }

        classBuilder.addMethod(dslWithLifeCycle.build())
        classBuilder.addMethod(dslWithoutLifeCycle.build())
    }

    private fun getTypeElements(
        elements: Set<Element>,
        supportedAnnotations: Set<Element>
    ): Set<TypeElement> {
        val typeElements = HashSet<TypeElement>()
        for (element in elements) {
            if (element is TypeElement && processSubElements(element, supportedAnnotations)) {
                typeElements.add(element)
            }
        }
        return typeElements
    }

    private fun processSubElements(
        element: TypeElement,
        supportedAnnotations: Set<Element>
    ): Boolean {
        for (subElement in element.enclosedElements) {
            if (processElementAnnotations(subElement, supportedAnnotations)) {
                return true
            }
        }
        return false
    }

    private fun processElementAnnotations(
        subElement: Element,
        supportedAnnotations: Set<Element>
    ): Boolean {
        for (mirror in subElement.annotationMirrors) {
            if (processAnnotation(supportedAnnotations, mirror)) {
                return true
            }
        }
        return false
    }

    private fun processAnnotation(
        supportedAnnotations: Set<Element>,
        mirror: AnnotationMirror
    ): Boolean {
        for (annotation in supportedAnnotations) {
            if (mirror.annotationType.asElement() == annotation) {
                return true
            }
        }
        return false
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(LiveDataDSL::class.java.canonicalName)
    }

    private fun getLifeCyclePackage(): String {
        return if (ANDROID_X) LIFE_CYCLE_PACKAGE_X else LIFE_CYCLE_PACKAGE
    }

    companion object {
        const val ANDROID_X = false
        const val LIFE_CYCLE_PACKAGE = "android.arch.lifecycle"
        const val LIFE_CYCLE_PACKAGE_X = "androidx.lifecycle"
    }

}
