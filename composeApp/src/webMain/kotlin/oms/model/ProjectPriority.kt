package oms.model

import oms.components.priorityDirectionLabel

/** The displayed source priority and the filter must refer to the same attribute. */
fun Project.priorityDirectionValue(): String = registry["priority"]?.takeIf(String::isNotBlank) ?: sector

fun Project.priorityDirectionKey(): String = oms.urp3.priorityDirectionKey(priorityDirectionValue())

fun Project.matchesPriorityDirection(filter: String?): Boolean = filter == null || priorityDirectionKey() == filter

fun Project.priorityDirectionLabel(): String = priorityDirectionValue().priorityDirectionLabel()
