package org.jetbrains.kotlin.taskmanagerdemo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TaskManagerDemoApplication

fun main(args: Array<String>) {
    runApplication<TaskManagerDemoApplication>(*args)
}
