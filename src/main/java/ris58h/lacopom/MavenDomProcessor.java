package ris58h.lacopom;

import org.jetbrains.idea.maven.dom.model.MavenDomDependencies;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomExclusion;
import org.jetbrains.idea.maven.dom.model.MavenDomExtension;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomProfile;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModelBase;

public class MavenDomProcessor {
    public final void process(MavenDomProjectModel projectModel) {
        processProjectModel(projectModel);
    }

    protected void onParent(MavenDomParent parent) {}
    protected void onProfile(MavenDomProfile profile) {}
    protected void onExtension(MavenDomExtension extension) {}
    protected void onDependency(MavenDomDependency dependency) {}
    protected void onExclusion(MavenDomExclusion exclusion) {}
    protected void onPlugin(MavenDomPlugin plugin) {}

    private void processProjectModel(MavenDomProjectModel projectModel) {
        onParent(projectModel.getMavenParent());
        processModelBase(projectModel);
        projectModel.getProfiles().getProfiles().forEach(profile -> {
            onProfile(profile);
            processModelBase(profile);
        });
        projectModel.getBuild().getExtensions().getExtensions().forEach(this::onExtension);
    }

    private void processDependencies(MavenDomDependencies dependencies) {
        dependencies.getDependencies().forEach(dependency -> {
            onDependency(dependency);
            dependency.getExclusions().getExclusions().forEach(this::onExclusion);
        });
    }

    private void processPlugin(MavenDomPlugin plugin) {
        onPlugin(plugin);
        processDependencies(plugin.getDependencies());
    }

    private void processModelBase(MavenDomProjectModelBase mb) {
        processDependencies(mb.getDependencies());
        processDependencies(mb.getDependencyManagement().getDependencies());
        mb.getBuild().getPlugins().getPlugins().forEach(this::processPlugin);
        mb.getBuild().getPluginManagement().getPlugins().getPlugins().forEach(this::processPlugin);
    }
}
