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

        Consumer<MavenDomDependencies> processDependencies = dependencies -> {
            dependencies.getDependencies().forEach(dependency -> addDependencyDescriptor(descriptors, dependency));
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
        processModelBase.accept(projectElement);
        projectElement.getProfiles().getProfiles().forEach(processModelBase::accept);

        return descriptors.isEmpty() ? FoldingDescriptor.EMPTY
                : descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }

    private static void addDependencyDescriptor(Collection<FoldingDescriptor> descriptors, MavenDomDependency dependency) {
        FoldingDescriptor foldingDescriptor = foldingDescriptor(dependency.getXmlTag());
        if (foldingDescriptor != null) {
            descriptors.add(foldingDescriptor);
        }
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
    private static FoldingDescriptor foldingDescriptor(XmlTag dependencyTag) {
        String dependencyDescription = describeDependency(dependencyTag);
        if (dependencyDescription == null) {
            return null;
        }
        TextRange rangeToFold = getRangeToFold(dependencyTag);
        if (rangeToFold == null) {
            return null;
        }
        return new NamedFoldingDescriptor(
                dependencyTag,
                rangeToFold.getStartOffset(),
                rangeToFold.getEndOffset(),
                null,
                " " + dependencyDescription);
    }

    //TODO systemPath, optional
    private static String describeDependency(XmlTag dependencyTag) {
        XmlTag exclusionsTag = dependencyTag.findFirstSubTag("exclusions");
        if (exclusionsTag != null) {
            return null;
        }
        XmlTag groupIdTag = dependencyTag.findFirstSubTag("groupId");
        XmlTag artifactIdTag = dependencyTag.findFirstSubTag("artifactId");
        if (artifactIdTag == null || groupIdTag == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(tagText(groupIdTag));
        appendPartIfNotNull(sb, artifactIdTag);
        appendPartIfNotNull(sb, dependencyTag.findFirstSubTag("type"));
        appendPartIfNotNull(sb, dependencyTag.findFirstSubTag("classifier"));
        appendPartIfNotNull(sb, dependencyTag.findFirstSubTag("version"));
        appendPartIfNotNull(sb, dependencyTag.findFirstSubTag("scope"));
        return sb.toString();
    }

    private static void appendPartIfNotNull(StringBuilder sb, XmlTag tag) {
        if (tag != null) {
            sb.append(':').append(tagText(tag));
        }
    }

    private static String tagText(XmlTag tag) {
        return tag.getValue().getTrimmedText();
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
