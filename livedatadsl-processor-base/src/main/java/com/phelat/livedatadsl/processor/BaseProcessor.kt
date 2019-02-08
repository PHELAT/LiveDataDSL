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
import javax.lang.model.type.TypeVariable
import javax.lang.model.util.Elements
import javax.tools.Diagnostic

abstract class BaseProcessor : AbstractProcessor() {

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
        val className = ParameterizedTypeName.get(typeElement.asType())
        val generatedClass = ClassName.get(packageName, "${typeName}_DSL")

        val classBuilder = TypeSpec.classBuilder(generatedClass).apply {
            addModifiers(Modifier.PUBLIC)
            superclass(className)
            if (className is ParameterizedTypeName) {
                for (typeArgument in className.typeArguments) {
                    if (typeArgument is TypeVariableName) {
                        addTypeVariable(typeArgument)
                    }
                }
            }
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
                val declaredTypeInheritance = DeclaredTypeInheritance(null, declaredType)
                val mutableLiveDataGenerics = getMutableLiveDataGeneric(declaredTypeInheritance)
                val observerGenerics = generateObserverGenerics(mutableLiveDataGenerics)
                val functionGenerics = generateFunctionGenerics(observerGenerics)

                val liveDataFieldName = "get${variableName.substring(0, 1)
                    .toUpperCase()}${variableName.substring(1)}"
                val observer = ClassName.get(getLifeCyclePackage(), "Observer")

                val rawLiveData = ClassName.get(getLifeCyclePackage(), "LiveData")
                val liveData = ParameterizedTypeName.get(
                    rawLiveData,
                    *mutableLiveDataGenerics.toTypedArray()
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
                    if (liveDataAnnotation.name.isBlank()) variableName else liveDataAnnotation.name,
                    functionGenerics,
                    liveData,
                    liveDataObservationCode,
                    liveDataObserveForEverCode,
                    mutableLiveDataGenerics,
                    observer,
                    rawFunctionResult,
                    classBuilder
                )
            }
        }
    }

    private fun generateObserverGenerics(typeNames: List<TypeName>): String {
        val generics = StringBuilder()
        typeNames.forEachIndexed { index, typeName ->
            generics.append(typeName.box().toString())
            if (index < typeNames.size - 1) {
                generics.append(", ")
            }
        }
        return generics.toString()
    }

    private fun getGenericTypeNames(typeArguments: List<TypeMirror>): List<TypeName> {
        val generics = mutableListOf<TypeName>()
        for (typeArgument in typeArguments) {
            val declaredType = if (typeArgument is DeclaredType) {
                typeArgument
            } else if (typeArgument is TypeVariable) {
                typeArgument.upperBound as DeclaredType
            } else {
                continue
            }
            val typeElement = declaredType.asElement() as TypeElement
            val packageName = elementUtils.getPackageOf(typeElement).qualifiedName
            val typeName = declaredType.asElement().simpleName
            val genericClassName = ClassName.get(packageName.toString(), typeName.toString())
            generics.add(genericClassName.box())
        }
        return generics
    }

    private fun getMutableLiveDataGeneric(declaredType: DeclaredTypeInheritance): List<TypeName> {
        val typeElement = declaredType.self.asElement() as TypeElement
        if (isDeclaredTypeLiveData(declaredType.self)) {
            val generics = getGenericTypeNames(declaredType.self.typeArguments)
            val genericClass = generics[0].box() as ClassName
            val isGenericNameObject = genericClass.simpleName() == "Object"
            val isGenericPackageObject = genericClass.packageName() == "java.lang"
            return if (isGenericNameObject && isGenericPackageObject && declaredType.son != null) {
                val liveDataGenericType = (declaredType.self.typeArguments[0] as TypeVariable)
                val liveDataGenericName = liveDataGenericType.asElement().simpleName
                getGenericTypeFromSon(declaredType, liveDataGenericName.toString())
            } else {
                generics
            }
        }
        val superClassDeclaredType = typeElement.superclass as DeclaredType
        return getMutableLiveDataGeneric(
            DeclaredTypeInheritance(declaredType, superClassDeclaredType)
        )
    }

    private fun getGenericTypeFromSon(
        declaredType: DeclaredTypeInheritance,
        genericName: String
    ): List<TypeName> {
        if (declaredType.son != null) {
            val sonTypeName = ParameterizedTypeName.get(declaredType.son.self.asElement().asType())
            val sonTypeArguments = (sonTypeName as ParameterizedTypeName).typeArguments
            for ((index, sonTypeArgument) in sonTypeArguments.withIndex()) {
                val sonGenericName = (sonTypeArgument as TypeVariableName).name
                if (genericName.contentEquals(sonGenericName)) {
                    val generic = declaredType.son.self.typeArguments[index]
                    val sonGenericType = if (generic is DeclaredType) {
                        generic
                    } else {
                        return getGenericTypeFromSon(declaredType.son, sonGenericName)
                    }
                    val sonTypeElement = sonGenericType.asElement() as TypeElement
                    val packageName = elementUtils.getPackageOf(sonTypeElement).qualifiedName
                    val typeName = sonGenericType.asElement().simpleName
                    val sonGenericClassName = ClassName.get(
                        packageName.toString(),
                        typeName.toString()
                    )
                    return listOf(sonGenericClassName)
                }
            }
            return getGenericTypeFromSon(declaredType.son, genericName)
        }
        return getGenericTypeNames(declaredType.self.typeArguments)
    }

    private fun isDeclaredTypeLiveData(declaredType: DeclaredType): Boolean {
        val typeElement = declaredType.asElement() as TypeElement
        val packageName = elementUtils.getPackageOf(typeElement).qualifiedName
        val variableTypeName = declaredType.asElement().simpleName
        val isPackageArch = packageName.contentEquals(getLifeCyclePackage())
        val isClassLiveData = variableTypeName.contentEquals("LiveData")
        val isClassMutableLiveData = variableTypeName.contentEquals("MutableLiveData")
        return isPackageArch && (isClassLiveData || isClassMutableLiveData)
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
                        "@Override public void onChanged($observerGenerics param) {function.invoke(${if (observerGenerics == "kotlin.Unit") ")" else "param)"};}" +
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
                        "@Override public void onChanged($observerGenerics param) {function.invoke(${if (observerGenerics == "kotlin.Unit") ")" else "param)"};}" +
                        "};",
                observer, observer
            )
            .add(
                "$liveDataFieldName().observeForever(observer);"
            )
            .add("return new \$T(observer, $liveDataFieldName());", rawFunctionResult)
            .build()
    }

    private fun generateFunctionGenerics(observerGenerics: String): String {
        val generics = StringBuilder()
        if (observerGenerics.isEmpty() || observerGenerics == "kotlin.Unit") {
            generics.append("kotlin.Unit")
        } else {
            generics.append("$observerGenerics, kotlin.Unit")
        }
        return generics.toString()
    }

    private fun generateDSLFunction(
        variableName: String,
        functionGenerics: String,
        liveData: ParameterizedTypeName,
        liveDataObservationCode: CodeBlock,
        liveDataObserveForEverCode: CodeBlock,
        observerGenerics: List<TypeName>,
        rawObserver: ClassName,
        rawFunctionResult: ClassName,
        classBuilder: TypeSpec.Builder
    ) {
        val function = ClassName.get(
            "kotlin.jvm.functions",
            if (functionGenerics == "kotlin.Unit") "Function0" else "Function1"
        )
        val lifecycleOwner = ClassName.get(getLifeCyclePackage(), "LifecycleOwner")

        val observer = ParameterizedTypeName.get(rawObserver, *observerGenerics.toTypedArray())
        val functionResult = ParameterizedTypeName.get(
            rawFunctionResult,
            observer.box(),
            liveData.box()
        )

        val dslWithLifeCycle = MethodSpec.methodBuilder(variableName).apply {
            addModifiers(Modifier.PUBLIC)
            returns(functionResult)
            addParameter(lifecycleOwner, "lifecycleOwner")
            addParameter(
                TypeVariableName.get("final ${function.packageName()}.${function.simpleName()}<$functionGenerics>"),
                "function"
            )
            addCode(liveDataObservationCode)
        }

        val dslWithoutLifeCycle = MethodSpec.methodBuilder(variableName).apply {
            addModifiers(Modifier.PUBLIC)
            returns(functionResult)
            addParameter(
                TypeVariableName.get("final ${function.packageName()}.${function.simpleName()}<$functionGenerics>"),
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

    abstract fun getLifeCyclePackage(): String

}
