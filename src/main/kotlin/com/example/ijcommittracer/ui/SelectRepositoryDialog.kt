package com.example.ijcommittracer.ui

import com.example.ijcommittracer.CommitTracerBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.components.JBList
import git4idea.repo.GitRepository
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ListSelectionModel

/**
 * Dialog for selecting a Git repository when multiple repositories are available.
 */
class SelectRepositoryDialog(
    project: Project,
    private val repositories: List<GitRepository>
) : DialogWrapper(project) {
    
    private val repoList = JBList(repositories.map { it.root.name }).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        selectedIndex = 0
    }
    
    private val component = LabeledComponent.create(repoList, CommitTracerBundle.message("dialog.repository.select")).apply {
        labelLocation = "North"
    }
    
    init {
        title = CommitTracerBundle.message("dialog.repository.select")
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        component.preferredSize = Dimension(400, 200)
        return component
    }
    
    /**
     * Returns the selected repository.
     */
    fun getSelectedRepository(): GitRepository {
        return repositories[repoList.selectedIndex]
    }
}
