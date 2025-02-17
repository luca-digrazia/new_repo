package com.taobao.android.builder.tasks.transform;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeArtifactsTransform;
import com.android.build.gradle.internal.transforms.*;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AtlasBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.FileCache;
import com.google.common.collect.ImmutableList;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.hook.dex.DexByteCodeConverterHook;
import com.taobao.android.builder.tasks.manager.transform.TransformManager;
import com.taobao.android.builder.tasks.transform.dex.AtlasDexArchiveBuilderTransform;
import com.taobao.android.builder.tasks.transform.dex.AtlasDexMergerTransform;
import com.taobao.android.builder.tasks.transform.dex.AtlasExternalLibsMergerTransform;
import com.taobao.android.builder.tasks.transform.dex.AtlasMultiDexListTransform;
import com.taobao.android.builder.tools.ReflectUtils;
import com.taobao.android.builder.tools.multidex.FastMultiDexer;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Verify.verifyNotNull;

/**
 * @author lilong
 * @create 2017-12-08 上午9:02
 */

public class TransformReplacer {

    private AppVariantContext variantContext;

    public TransformReplacer(AppVariantContext variantContext) {
        this.variantContext = variantContext;
    }

    public void replaceDexArchiveBuilderTransform(BaseVariantOutput vod) {
        List<TransformTask> list = TransformManager.findTransformTaskByTransformType(variantContext,
                DexArchiveBuilderTransform.class);

        DefaultDexOptions dexOptions = variantContext.getAppExtension().getDexOptions();

        boolean minified = variantContext.getScope().getCodeShrinker() != null;

        ProjectOptions projectOptions = variantContext.getScope().getGlobalScope().getProjectOptions();

        FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());
        for (TransformTask transformTask: list){
            AtlasDexArchiveBuilderTransform atlasDexArchiveBuilderTransform = new AtlasDexArchiveBuilderTransform(variantContext,  vod,
                    dexOptions,
                    variantContext.getScope().getGlobalScope().getAndroidBuilder().getErrorReporter(),
                    userLevelCache,
                    variantContext.getScope().getMinSdkVersion().getFeatureLevel(),
                    variantContext.getScope().getDexer(),
                    projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS),
                    projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE),
                    projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE),
                    variantContext.getScope().getVariantConfiguration().getBuildType().isDebuggable());
            atlasDexArchiveBuilderTransform.setTransformTask(transformTask);
            ReflectUtils.updateField(transformTask,"transform",atlasDexArchiveBuilderTransform);
        }

    }

    public void replaceDataBindingMergeArtifactsTransform() {
        List<TransformTask> list = TransformManager.findTransformTaskByTransformType(variantContext,
                DataBindingMergeArtifactsTransform.class);
        for (TransformTask transformTask: list){
            File outFolder =
                    new File(
                            variantContext.getScope().getBuildFolderForDataBindingCompiler(),
                            DataBindingBuilder.ARTIFACT_FILES_DIR_FROM_LIBS);
            AtlasDataBindingMergeArtifactsTransform dataBindingMergeArtifactsTransform = new AtlasDataBindingMergeArtifactsTransform(variantContext,Logging.getLogger(AtlasDataBindingMergeArtifactsTransform.class),outFolder);
            ReflectUtils.updateField(transformTask,"transform",dataBindingMergeArtifactsTransform);
        }

    }

    @Nullable
    private FileCache getUserDexCache(boolean isMinifiedEnabled, boolean preDexLibraries) {
        if (!preDexLibraries || isMinifiedEnabled) {
            return null;
        }
        return getUserIntermediatesCache();
    }

    @Nullable
    private FileCache getUserIntermediatesCache() {
        if (variantContext.getScope().getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
            return variantContext.getScope().getGlobalScope().getBuildCache();
        } else {
            return null;
        }
    }


    public void replaceDexExternalLibMerge(BaseVariantOutput vod) {
        List<TransformTask> list = TransformManager.findTransformTaskByTransformType(variantContext,
                ExternalLibsMergerTransform.class);
        DexingType dexingType = variantContext.getScope().getDexingType();
        DexMergerTool dexMergerTool = variantContext.getScope().getDexMerger();
         int sdkVerision = variantContext.getScope().getMinSdkVersion().getFeatureLevel();
        boolean debug = variantContext.getScope().getVariantConfiguration().getBuildType().isDebuggable();
        ErrorReporter errorReporter = variantContext.getScope().getGlobalScope().getAndroidBuilder().getErrorReporter();
        DexMergerTransformCallable.Factory factory = (dexingType1, processOutput, dexOutputDir, dexArchives, mainDexList, forkJoinPool, dexMerger, minSdkVersion, isDebuggable) -> new DexMergerTransformCallable(dexingType1,processOutput,dexOutputDir,dexArchives,mainDexList,forkJoinPool,dexMerger,minSdkVersion,isDebuggable);
        for (TransformTask transformTask: list){
            transformTask.setEnabled(false);
//            AtlasExternalLibsMergerTransform atlasExternalLibsMergerTransform = new AtlasExternalLibsMergerTransform(variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)),
//                    dexingType,
//                    dexMergerTool,
//                    sdkVerision,
//                    debug,
//                    errorReporter,
//                    factory);
//                    ReflectUtils.updateField(transformTask,"transform",atlasExternalLibsMergerTransform);

        }
    }

    public void replaceDexMerge(BaseVariantOutput vod) {
        List<TransformTask> list = TransformManager.findTransformTaskByTransformType(variantContext,
                DexMergerTransform.class);
        DexingType dexingType = variantContext.getScope().getDexingType();
        DexMergerTool dexMergerTool = variantContext.getScope().getDexMerger();
        int sdkVerision = variantContext.getScope().getMinSdkVersion().getFeatureLevel();
        boolean debug = variantContext.getScope().getVariantConfiguration().getBuildType().isDebuggable();
        ErrorReporter errorReporter = variantContext.getScope().getGlobalScope().getAndroidBuilder().getErrorReporter();
        for (TransformTask transformTask : list) {
            AtlasDexMergerTransform dexMergerTransform = new AtlasDexMergerTransform(
                    variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod))
                    , dexingType,
                    dexingType == DexingType.LEGACY_MULTIDEX
                            ? variantContext.getProject().files(variantContext.getScope().getMainDexListFile())
                            : null,
                    errorReporter, dexMergerTool, sdkVerision, debug);
            ReflectUtils.updateField(transformTask, "transform", dexMergerTransform);

        }
    }

    public void replaceMultiDexListTransform() {
        List<TransformTask> list = null;
        FastMultiDexer fastMultiDexer = new FastMultiDexer(variantContext);
        if (usingIncrementalDexing(variantContext.getScope())) {
            list = TransformManager.findTransformTaskByTransformType(variantContext,
                    MainDexListTransform.class);
        }else {
            list = TransformManager.findTransformTaskByTransformType(variantContext,
                    MultiDexTransform.class);
        }
        if (list.size() > 0 && fastMultiDexer.isFastMultiDexEnabled()){
            com.android.build.gradle.internal.dsl.DexOptions dexOptions = variantContext.getScope().getGlobalScope().getExtension().getDexOptions();
            AtlasMultiDexListTransform atlasMultiDexListTransform = new AtlasMultiDexListTransform(variantContext.getScope(),dexOptions);
            for (TransformTask transformTask:list){
                ReflectUtils.updateField(transformTask, "transform", atlasMultiDexListTransform);
                transformTask.doFirst(task -> AtlasBuildContext.androidBuilderMap.get(task.getProject()).multiDexer = (AtlasBuilder.MultiDexer) fastMultiDexer);
                transformTask.doLast(task -> AtlasBuildContext.androidBuilderMap.get(task.getProject()).multiDexer = null);
            }
        }
    }

    private boolean usingIncrementalDexing(@NonNull VariantScope variantScope) {
        if (!variantScope.getGlobalScope().getProjectOptions().get(BooleanOption.ENABLE_DEX_ARCHIVE)) {
            return false;
        }
        if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
            return true;
        }

        // In release builds only D8 can be used. See b/37140568 for details.
        return variantScope.getGlobalScope().getProjectOptions().get(BooleanOption.ENABLE_D8);
    }

    public void replaceProguardTransform() {

        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, ProGuardTransform.class);

        for (TransformTask transformTask : baseTransforms) {

            AtlasProguardTransform newTransform = new AtlasProguardTransform(variantContext);

            ReflectUtils.updateField(transformTask, "transform",
                    newTransform);
            newTransform.oldTransform = (ProGuardTransform) transformTask.getTransform();
//
        }
    }

    public void replaceDexTransform(AppVariantContext appVariantContext, BaseVariantOutput vod) {
        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, DexTransform.class);

        DefaultDexOptions dexOptions= appVariantContext.getAppExtension().getDexOptions();
        DexingType dexingType = appVariantContext.getScope().getDexingType();
        DexByteCodeConverterHook dexByteCodeConverterHook = new DexByteCodeConverterHook(variantContext
                ,variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod))
                , LoggerWrapper.getLogger(DexByteCodeConverterHook.class)
                ,appVariantContext.getScope().getGlobalScope().getAndroidBuilder().getTargetInfo()
                ,new GradleJavaProcessExecutor(appVariantContext.getProject())
                ,appVariantContext.getProject().getLogger().isEnabled(LogLevel.INFO)
                ,new ExtraModelInfo(appVariantContext.getScope().getGlobalScope().getProjectOptions(), appVariantContext.getProject().getLogger()));

        for (TransformTask transformTask : baseTransforms) {
            DexTransform newTransform = new DexTransform(dexOptions
                    , dexingType
                    , false
                    , appVariantContext.getProject().files(variantContext.getScope().getMainDexListFile())
                    , verifyNotNull(appVariantContext.getScope().getGlobalScope().getAndroidBuilder().getTargetInfo(), "Target Info not set.")
                    , dexByteCodeConverterHook
                    , appVariantContext.getScope().getGlobalScope().getAndroidBuilder().getErrorReporter()
                    , variantContext.getScope().getMinSdkVersion().getFeatureLevel());
            ReflectUtils.updateField(transformTask, "transform",
                    newTransform);
        }

        }

    public void replaceMergeJavaResourcesTransform(AppVariantContext appVariantContext, BaseVariantOutput vod) {
        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, MergeJavaResourcesTransform.class);
        for (TransformTask transformTask:baseTransforms){
            MergeJavaResourcesTransform transform = (MergeJavaResourcesTransform) transformTask.getTransform();
            PackagingOptions packagingOptions = (PackagingOptions) ReflectUtils.getField(transform,"packagingOptions");
            packagingOptions.exclude("**.aidl");
            Set<? super QualifiedContent.Scope> mergeScopes = (Set<? super QualifiedContent.Scope>) ReflectUtils.getField(transform,"mergeScopes");
            Set<QualifiedContent.ContentType> mergedType = (Set<QualifiedContent.ContentType>) ReflectUtils.getField(transform,"mergedType");
            String name = (String) ReflectUtils.getField(transform,"name");
            AtlasMergeJavaResourcesTransform atlasMergeJavaResourcesTransform = new AtlasMergeJavaResourcesTransform(appVariantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)),packagingOptions,mergeScopes,mergedType.iterator().next(),name,appVariantContext.getScope());
            ReflectUtils.updateField(transformTask, "transform",
                    atlasMergeJavaResourcesTransform);
        }

    }
}
