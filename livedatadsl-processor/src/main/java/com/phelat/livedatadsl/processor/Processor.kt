package com.phelat.livedatadsl.processor

class Processor : BaseProcessor() {

    override fun getLifeCyclePackage(): String {
        return "android.arch.lifecycle"
    }

}
