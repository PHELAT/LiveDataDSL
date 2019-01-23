# LiveDataDSL
Generates observe DSL for android architecture component's LiveData.
```kotlin
loginViewModel.apply {
    mySampleLiveData { userName ->
        println(userName)
        ...
    }
    myOtherLiveData {
        println(it)
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
    sampleLiveData {
        // This is where observation happens, you can use `it` receiver as observed value
        ...
    }
    myOtherLiveData { myValue ->
        // This is where observation happens, you can use `myValue` receiver as observed value
        ...
    }
}
```

## Dependency
```groovy
dependencies {
    implementation "com.phelat:livedatadsl:1.0.0-alpha2"
    kapt "com.phelat:livedatadsl-processor:1.0.0-alpha2"
}
```
