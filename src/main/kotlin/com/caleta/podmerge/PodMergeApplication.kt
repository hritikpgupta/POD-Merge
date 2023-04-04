package com.caleta.podmerge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PodMergeApplication
fun main(args: Array<String>) {
	try {
		runApplication<PodMergeApplication>(*args)
	}catch (e : Exception){
		e.printStackTrace()
	}
}
