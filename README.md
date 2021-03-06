# LiveDataDSL [![](https://api.bintray.com/packages/m4hdi/LiveDataDSL/LiveDataDSL/images/download.svg)](https://bintray.com/beta/#/m4hdi/LiveDataDSL?tab=packages) [![Awesome Kotlin Badge](https://kotlin.link/awesome-kotlin.svg)](https://github.com/KotlinBy/awesome-kotlin)
Generates DSL for LiveData observation.
```kotlin
loginViewModel.apply {
    mySampleLiveData(this@MainActivity) { userName ->
        println(userName)
        ...
    }
    liveDataWithoutLifeCycleOwner { userName ->
        println(userName)
        ...
    }
}
```


## How to use  
1. You need to add `@LiveDataDSL` annotation on your LiveData:  
```kotlin
@LiveDataDSL
protected val sampleLiveData = MutableLiveData<String>()

@LiveDataDSL
protected val myOtherLiveData = MutableLiveData<Int>()
```
Also make sure that your LiveData has `protected` or `public` modifier.

2. Make sure that your view model class is extendable:
```kotlin
open class SampleViewModel : ViewModel() {
    ...
}
```
OR
```kotlin
abstract class SampleViewModel : ViewModel() {
    ...
}
```

3. Build your project, and use the generated ViewModel class in your view(Fragment, Activity or whatever).  
The generated ViewModel will be named `${ORIGINAL_CLASS_NAME}_DSL.kt`, for example, if your ViewModel is named `SampleViewModel.kt`, then the generated ViewModel will be named `SampleViewModel_DSL.kt`

4. Use the generated ViewModel:
```kotlin
val sampleViewModel = ViewModelProviders.of(this)[SampleViewModel_DSL::class.java]
sampleViewModel.apply{
    sampleLiveData(this@MainActivity) {
        // This is where observation happens, you can use `it` receiver as observed value
        ...
    }
    myOtherLiveData(this@MainActivity) { myValue ->
        // This is where observation happens, you can use `myValue` receiver as observed value
        ...
    }
}
```
### Observe without LifecycleOwner
You can also observe your live data without passing LifecycleOwner, but it will use `observeForever`.
```kotlin
private lateinit var function: FunctionResult<Observer<String>, LiveData<String>>

override fun onCreate(bundle: Bundle?) {
    val sampleViewModel = ViewModelProviders.of(this)[SampleViewModel_DSL::class.java]
    sampleViewModel.apply{
        function = sampleLiveData {
            // This is where observation happens, you can use `it` receiver as observed value
            ...
        }
        myOtherLiveData { myValue ->
            // This is where observation happens, you can use `myValue` receiver as observed value
            ...
        }
    }
}

override fun onDestroy() {
    super.onDestroy()
    function.apply { liveData.removeObservers(observer) }
}
```
### Custom name for DSL function
You can set custom name for the DSL function through `@LiveData(name = "customName")`.
```kotlin
open class SampleViewModel : ViewModel() {
    @LiveData("helloWorld")
    protected val customLiveData = MutableLiveData<Any>()
}

val sampleViewModel = ViewModelProviders.of(this)[SampleViewModel_DSL::class.java].apply {
    helloWorld {
        ...
    }
}
```

## Dependency
```groovy
dependencies {
    implementation "com.phelat:livedatadsl:1.0.0-alpha7"
    kapt "com.phelat:livedatadsl-processor:1.0.0-alpha7"
}
```
#### For AndroidX
```groovy
dependencies {
    implementation "com.phelat:livedatadsl:1.0.0-alpha7"
    kapt "com.phelat:livedatadsl-processor-x:1.0.0-alpha7"
}
```
