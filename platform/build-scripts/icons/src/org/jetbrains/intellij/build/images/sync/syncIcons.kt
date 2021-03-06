// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File

internal fun syncAdded(added: Collection<String>,
                       sourceRepoMap: Map<String, GitObject>,
                       targetDir: File, targetRepo: (File) -> File) {
  callSafely {
    val unversioned = mutableMapOf<File, MutableList<String>>()
    added.forEach {
      val target = File(targetDir, it)
      if (target.exists()) log("$it already exists in target repo!")
      val source = sourceRepoMap[it]!!.file
      source.copyTo(target, overwrite = true)
      val repo = targetRepo(target)
      if (!unversioned.containsKey(repo)) unversioned[repo] = mutableListOf()
      unversioned[repo]!!.add(target.relativeTo(repo).path)
    }
    unversioned.forEach { repo, add ->
      addChangesToGit(add, repo)
    }
  }
}

internal fun syncModified(modified: Collection<String>,
                          targetRepoMap: Map<String, GitObject>,
                          sourceRepoMap: Map<String, GitObject>) {
  callSafely {
    modified.forEach {
      val target = targetRepoMap[it]!!.file
      val source = sourceRepoMap[it]!!.file
      source.copyTo(target, overwrite = true)
    }
  }
}

internal fun syncRemoved(removed: Collection<String>,
                         targetRepoMap: Map<String, GitObject>) {
  callSafely {
    removed.map { targetRepoMap[it]!!.file }.forEach {
      if (!it.delete()) {
        log("Failed to delete ${it.absolutePath}")
      }
      else if (it.parentFile.list().isEmpty()) {
        it.parentFile.delete()
      }
    }
  }
}