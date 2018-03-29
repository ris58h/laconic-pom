package ris58h.lacopom;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PomFoldingBuilder extends FoldingBuilderEx {
    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        if (!(root instanceof XmlFile)) {
            return FoldingDescriptor.EMPTY;
        }

        DomManager manager = DomManager.getDomManager(root.getProject());
        DomFileElement<MavenDomProjectModel> fileElement = manager.getFileElement(((XmlFile) root), MavenDomProjectModel.class);
        if (fileElement == null) {
            return FoldingDescriptor.EMPTY;
        }

        Collection<FoldingDescriptor> descriptors = new ArrayList<>();

        BiConsumer<XmlTag, String> addDescriptorIfPossible = (xmlTag, placeholder) -> {
            FoldingDescriptor foldingDescriptor = foldingDescriptor(xmlTag, placeholder);
            if (foldingDescriptor != null) {
                descriptors.add(foldingDescriptor);
            }
        };
        Consumer<MavenDomDependencies> processDependencies = dependencies -> {
            dependencies.getDependencies().forEach(dependency -> {
                addDescriptorIfPossible.accept(dependency.getXmlTag(), describeDependency(dependency));
                dependency.getExclusions().getExclusions().forEach(exclusion -> {
                    addDescriptorIfPossible.accept(exclusion.getXmlTag(), describeExclusion(exclusion));
                });
            });
        };
        Consumer<MavenDomPlugin> processPlugin = plugin -> {
            processDependencies.accept(plugin.getDependencies());
        };
        Consumer<MavenDomProjectModelBase> processModelBase = mb -> {
            processDependencies.accept(mb.getDependencies());
            processDependencies.accept(mb.getDependencyManagement().getDependencies());

            mb.getBuild().getPlugins().getPlugins().forEach(processPlugin);
            mb.getBuild().getPluginManagement().getPlugins().getPlugins().forEach(processPlugin);
        };

        MavenDomProjectModel projectElement = fileElement.getRootElement();
        MavenDomParent parent = projectElement.getMavenParent();
        addDescriptorIfPossible.accept(parent.getXmlTag(), describeParent(parent));
        processModelBase.accept(projectElement);
        projectElement.getProfiles().getProfiles().forEach(profile -> {
            addDescriptorIfPossible.accept(profile.getXmlTag(), describeProfile(profile));
            processModelBase.accept(profile);
        });

        return descriptors.isEmpty() ? FoldingDescriptor.EMPTY
                : descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }

    @Nullable
    private static FoldingDescriptor foldingDescriptor(XmlTag xmlTag, String placeholder) {
        if (placeholder == null) {
            return null;
        }
        TextRange rangeToFold = getRangeToFold(xmlTag);
        if (rangeToFold == null) {
            return null;
        }
        return new NamedFoldingDescriptor(
                xmlTag,
                rangeToFold.getStartOffset(),
                rangeToFold.getEndOffset(),
                null,
                " " + placeholder);
    }


    private static boolean hasId(MavenDomShortArtifactCoordinates artifactCoordinates) {
        String groupId = artifactCoordinates.getGroupId().getStringValue();
        String artifactId = artifactCoordinates.getArtifactId().getStringValue();
        return groupId != null && artifactId != null;
    }

    private static String describeParent(MavenDomParent parent) {
        if (!hasId(parent)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(parent.getGroupId().getStringValue());
        appendPartIfNotNull(sb, parent.getArtifactId().getStringValue());
        appendPartIfNotNull(sb, parent.getVersion().getStringValue());
        if (parent.getRelativePath().exists()) {
            sb.append(" ...");
        }
        return sb.toString();
    }

    private static String describeProfile(MavenDomProfile profile) {
        String id = profile.getId().getStringValue();
        if (id == null) {
            return null;
        }
        for (XmlTag subTag : profile.getXmlTag().getSubTags()) {
            if (!subTag.getLocalName().equals("id")) {
                return id + " ...";
            }
        }
        return id;
    }

    private static String describeDependency(MavenDomDependency dependency) {
        if (!hasId(dependency)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(dependency.getGroupId().getStringValue());
        appendPartIfNotNull(sb, dependency.getArtifactId().getStringValue());
        appendPartIfNotNull(sb, dependency.getType().getStringValue());
        appendPartIfNotNull(sb, dependency.getClassifier().getStringValue());
        appendPartIfNotNull(sb, dependency.getVersion().getStringValue());
        appendPartIfNotNull(sb, dependency.getScope().getStringValue());
        if (dependency.getExclusions().exists()) {
            sb.append(" ...");
        }
        return sb.toString();
    }

    private static String describeExclusion(MavenDomExclusion exclusion) {
        if (!hasId(exclusion)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(exclusion.getGroupId().getStringValue());
        appendPartIfNotNull(sb, exclusion.getArtifactId().getStringValue());
        return sb.toString();
    }

    private static void appendPartIfNotNull(StringBuilder sb, String s) {
        if (s != null) {
            sb.append(':').append(s);
        }
    }

    //TODO: it's a copypaste from com.intellij.lang.XmlCodeFoldingBuilder
    private static final TokenSet XML_ATTRIBUTE_SET = TokenSet.create(XmlElementType.XML_ATTRIBUTE);
    @Nullable
    private static TextRange getRangeToFold(XmlTag xmlTag) {
        XmlToken tagNameElement = XmlTagUtil.getStartTagNameElement(xmlTag);
        if (tagNameElement == null) return null;

        final ASTNode tagNode = xmlTag.getNode();
        int end = tagNode.getLastChildNode().getTextRange().getEndOffset() - 1;  // last child node can be another tag in unbalanced tree
        ASTNode[] attributes = tagNode.getChildren(XML_ATTRIBUTE_SET);

        if (attributes.length > 0) {
            ASTNode lastAttribute = attributes[attributes.length - 1];
            ASTNode lastAttributeBeforeCR = null;

            for (ASTNode child = tagNode.getFirstChildNode(); child != lastAttribute.getTreeNext(); child = child.getTreeNext()) {
                if (child.getElementType() == XmlElementType.XML_ATTRIBUTE) {
                    lastAttributeBeforeCR = child;
                } else if (child.getPsi() instanceof PsiWhiteSpace) {
                    if (child.textContains('\n')) break;
                }
            }

            if (lastAttributeBeforeCR != null) {
                int attributeEnd = lastAttributeBeforeCR.getTextRange().getEndOffset();
                return new UnfairTextRange(attributeEnd, end);
            }
        }
        int nameEnd = tagNameElement.getTextRange().getEndOffset();
        return new UnfairTextRange(nameEnd, end);
    }
}
