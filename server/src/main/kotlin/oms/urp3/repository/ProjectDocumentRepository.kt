package oms.urp3.repository

import oms.urp3.domain.ProjectDocument

interface ProjectDocumentRepository { fun findAll(): List<ProjectDocument>; fun findByProjectId(projectId: Long): List<ProjectDocument>; fun findByUuid(projectId: Long, uuid: String): ProjectDocument?; fun create(document: ProjectDocument); fun delete(projectId: Long, uuid: String): Boolean }
