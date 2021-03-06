/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.project.ProjectKt;
import com.intellij.util.UriUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;

/**
 * Serves as a container for all existing template manager types and loads corresponding templates upon creation (at construction time).
 *
 * @author Rustam Vishnyakov
 */
class FileTemplatesLoader {
  private static final Logger LOG = Logger.getInstance(FileTemplatesLoader.class);

  static final String TEMPLATES_DIR = "fileTemplates";
  private static final String DEFAULT_TEMPLATES_ROOT = TEMPLATES_DIR;
  private static final String DESCRIPTION_FILE_EXTENSION = "html";
  private static final String DESCRIPTION_EXTENSION_SUFFIX = "." + DESCRIPTION_FILE_EXTENSION;

  private final FTManager myDefaultTemplatesManager;
  private final FTManager myInternalTemplatesManager;
  private final FTManager myPatternsManager;
  private final FTManager myCodeTemplatesManager;
  private final FTManager myJ2eeTemplatesManager;

  private final FTManager[] myAllManagers;

  private static final String INTERNAL_DIR = "internal";
  private static final String INCLUDES_DIR = "includes";
  private static final String CODE_TEMPLATES_DIR = "code";
  private static final String J2EE_TEMPLATES_DIR = "j2ee";
  private final FileTypeManagerEx myTypeManager;

  private URL myDefaultTemplateDescription;
  private URL myDefaultIncludeDescription;

  FileTemplatesLoader(@NotNull FileTypeManagerEx typeManager, @Nullable Project project) {
    myTypeManager = typeManager;
    File configDir = project == null || project.isDefault()
                     ? new File(PathManager.getConfigPath(), TEMPLATES_DIR)
                     : new File(UriUtil.trimTrailingSlashes(ProjectKt.getStateStore(project).getDirectoryStorePath(true)) + "/" + TEMPLATES_DIR);
    myDefaultTemplatesManager = new FTManager(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, configDir);
    myInternalTemplatesManager = new FTManager(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY, new File(configDir, INTERNAL_DIR), true);
    myPatternsManager = new FTManager(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, new File(configDir, INCLUDES_DIR));
    myCodeTemplatesManager = new FTManager(FileTemplateManager.CODE_TEMPLATES_CATEGORY, new File(configDir, CODE_TEMPLATES_DIR));
    myJ2eeTemplatesManager = new FTManager(FileTemplateManager.J2EE_TEMPLATES_CATEGORY, new File(configDir, J2EE_TEMPLATES_DIR));
    myAllManagers = new FTManager[]{
      myDefaultTemplatesManager,
      myInternalTemplatesManager,
      myPatternsManager,
      myCodeTemplatesManager,
      myJ2eeTemplatesManager};

    loadDefaultTemplates(configDir);
    for (FTManager manager : myAllManagers) {
      manager.loadCustomizedContent();
    }
  }

  @NotNull
  FTManager[] getAllManagers() {
    return myAllManagers;
  }

  @NotNull
  FTManager getDefaultTemplatesManager() {
    return new FTManager(myDefaultTemplatesManager);
  }

  @NotNull
  FTManager getInternalTemplatesManager() {
    return new FTManager(myInternalTemplatesManager);
  }

  @NotNull
  FTManager getPatternsManager() {
    return new FTManager(myPatternsManager);
  }

  @NotNull
  FTManager getCodeTemplatesManager() {
    return new FTManager(myCodeTemplatesManager);
  }

  @NotNull
  FTManager getJ2eeTemplatesManager() {
    return new FTManager(myJ2eeTemplatesManager);
  }

  URL getDefaultTemplateDescription() {
    return myDefaultTemplateDescription;
  }

  URL getDefaultIncludeDescription() {
    return myDefaultIncludeDescription;
  }

  private void loadDefaultTemplates(@NotNull File configDir) {
    Set<URL> processedUrls = new HashSet<>();
    Set<ClassLoader> processedLoaders = new HashSet<>();
    for (PluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (plugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)plugin).isEnabled()) {
        final ClassLoader loader = plugin.getPluginClassLoader();
        if (loader instanceof PluginClassLoader && ((PluginClassLoader)loader).getUrls().isEmpty() ||
            !processedLoaders.add(loader)) {
          continue; // test or development mode, when IDEA_CORE's loader contains all the classpath
        }
        try {
          final Enumeration<URL> systemResources = loader.getResources(DEFAULT_TEMPLATES_ROOT);
          if (systemResources.hasMoreElements()) {
            while (systemResources.hasMoreElements()) {
              final URL url = systemResources.nextElement();
              if (processedUrls.contains(url)) {
                continue;
              }
              processedUrls.add(url);
              loadDefaultsFromRoot(url, configDir);
            }
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void loadDefaultsFromRoot(@NotNull URL root, @NotNull File configDir) throws IOException {
    final List<String> children = UrlUtil.getChildrenRelativePaths(root);
    if (children.isEmpty()) {
      return;
    }
    final Set<String> descriptionPaths = new HashSet<>();
    for (String path : children) {
      if (path.equals("default.html")) {
        myDefaultTemplateDescription = UrlClassLoader.internProtocol(new URL(UriUtil.trimTrailingSlashes(root.toExternalForm()) + "/" + path));
      }
      else if (path.equals("includes/default.html")) {
        myDefaultIncludeDescription = UrlClassLoader.internProtocol(new URL(UriUtil.trimTrailingSlashes(root.toExternalForm()) + "/" + path));
      }
      else if (path.endsWith(DESCRIPTION_EXTENSION_SUFFIX)) {
        descriptionPaths.add(path);
      }
    }
    for (final String path : children) {
      if (!path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) continue;
      for (FTManager ftManager : myAllManagers) {
        File managerRoot = ftManager.getConfigRoot(false);
        String relativePath = configDir.equals(managerRoot) ? "" : FileUtil.getRelativePath(configDir, managerRoot)+"/";
        String prefix = FileUtil.toSystemIndependentName(relativePath);
        if (matchesPrefix(path, prefix)) {
          String filename = path.substring(prefix.length(), path.length() - FTManager.TEMPLATE_EXTENSION_SUFFIX.length());
          String extension = myTypeManager.getExtension(filename);
          String templateName = filename.substring(0, filename.length() - extension.length() - 1);
          URL templateUrl = UrlClassLoader.internProtocol(new URL(UriUtil.trimTrailingSlashes(root.toExternalForm())+ "/" + path));
          String descriptionPath = getDescriptionPath(prefix, templateName, extension, descriptionPaths);
          URL descriptionUrl = descriptionPath == null ? null :
                               UrlClassLoader.internProtocol(new URL(UriUtil.trimTrailingSlashes(root.toExternalForm()) + "/" + descriptionPath));
          assert templateUrl != null;
          ftManager.addDefaultTemplate(new DefaultTemplate(templateName, extension, templateUrl, descriptionUrl));
          break; // FTManagers loop
        }
      }
    }
  }

  private static boolean matchesPrefix(@NotNull String path, @NotNull String prefix) {
    if (prefix.isEmpty()) {
      return !path.contains("/");
    }
    return FileUtil.startsWith(path, prefix) && !path.substring(prefix.length()).contains("/");
  }

  //Example: templateName="NewClass"   templateExtension="java"
  @Nullable
  private static String getDescriptionPath(@NotNull String pathPrefix,
                                           @NotNull String templateName,
                                           @NotNull String templateExtension,
                                           @NotNull Set<String> descriptionPaths) {
    final Locale locale = Locale.getDefault();

    String descName = MessageFormat
      .format("{0}.{1}_{2}_{3}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension,
              locale.getLanguage(), locale.getCountry());
    String descPath = pathPrefix.isEmpty() ? descName : pathPrefix + descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }

    descName = MessageFormat.format("{0}.{1}_{2}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension, locale.getLanguage());
    descPath = pathPrefix.isEmpty() ? descName : pathPrefix + descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }

    descName = templateName + "." + templateExtension + DESCRIPTION_EXTENSION_SUFFIX;
    descPath = pathPrefix.isEmpty() ? descName : pathPrefix + descName;
    if (descriptionPaths.contains(descPath)) {
      return descPath;
    }
    return null;
  }
}
