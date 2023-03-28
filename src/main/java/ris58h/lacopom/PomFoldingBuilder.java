package ris58h.lacopom;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
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
import com.intellij.util.xml.DomElement;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomExclusion;
import org.jetbrains.idea.maven.dom.model.MavenDomExtension;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomPluginExecution;
import org.jetbrains.idea.maven.dom.model.MavenDomProfile;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;

import java.util.*;

public class PomFoldingBuilder extends FoldingBuilderEx {
    private static final FoldingDescriptor[] EMPTY_FOLDING_DESCRIPTOR_ARRAY = new FoldingDescriptor[0];
    private static final char PART_SEPARATOR = ':';
    private static final String MORE_ENDING = " ...";

    @NotNull
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        if (!(root instanceof XmlFile)) {
            return EMPTY_FOLDING_DESCRIPTOR_ARRAY;
        }

        MavenDomProjectModel projectModel = MavenDomUtil.getMavenDomModel(((XmlFile) root), MavenDomProjectModel.class);
        if (projectModel == null) {
            return EMPTY_FOLDING_DESCRIPTOR_ARRAY;
        }

        Collection<FoldingDescriptor> descriptors = descriptors(projectModel);

        return descriptors.isEmpty() ? EMPTY_FOLDING_DESCRIPTOR_ARRAY
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
        return new FoldingDescriptor(
                xmlTag,
                rangeToFold.getStartOffset(),
                rangeToFold.getEndOffset(),
                null,
                " " + placeholder);
    }

    private static Collection<FoldingDescriptor> descriptors(MavenDomProjectModel projectModel) {
        Collection<FoldingDescriptor> descriptors = new ArrayList<>();
        new MavenDomProcessor() {
            private void addDescriptorIfPossible(XmlTag xmlTag, String placeholder) {
                FoldingDescriptor foldingDescriptor = foldingDescriptor(xmlTag, placeholder);
                if (foldingDescriptor != null) {
                    descriptors.add(foldingDescriptor);
                }
            }

            @Override
            protected void onParent(MavenDomParent parent) {
                addDescriptorIfPossible(parent.getXmlTag(), describeParent(parent));
            }

            @Override
            protected void onProfile(MavenDomProfile profile) {
                addDescriptorIfPossible(profile.getXmlTag(), describeProfile(profile));
            }

            @Override
            protected void onExtension(MavenDomExtension extension) {
                addDescriptorIfPossible(extension.getXmlTag(), describeExtension(extension));
            }

            @Override
            protected void onDependency(MavenDomDependency dependency) {
                addDescriptorIfPossible(dependency.getXmlTag(), describeDependency(dependency));
            }

            @Override
            protected void onExclusion(MavenDomExclusion exclusion) {
                addDescriptorIfPossible(exclusion.getXmlTag(), describeExclusion(exclusion));
            }

            @Override
            protected void onPlugin(MavenDomPlugin plugin) {
                addDescriptorIfPossible(plugin.getXmlTag(), describePlugin(plugin));
            }

            @Override
            protected void onExecution(MavenDomPluginExecution execution) {
                addDescriptorIfPossible(execution.getXmlTag(), describeExecution(execution));
            }
        }.process(projectModel);
        return descriptors;
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
        String version = parent.getVersion().getStringValue();
        if (version == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(parent.getGroupId().getStringValue());
        appendPartIfNotNull(sb, parent.getArtifactId().getStringValue());
        appendPartIfNotNull(sb, version);
        if (parent.getRelativePath().exists()) {
            sb.append(MORE_ENDING);
        }
        return sb.toString();
    }

    private static String describeProfile(MavenDomProfile profile) {
        String id = profile.getId().getStringValue();
        if (id == null) {
            return null;
        }
        boolean hasMore = hasSubTagsExcept(profile, Set.of("id"));
        return hasMore ? id + MORE_ENDING : id;
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
            sb.append(MORE_ENDING);
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

    private static String describePlugin(MavenDomPlugin plugin) {
        String artifactId = plugin.getArtifactId().getStringValue();
        if (artifactId == null) {
            return null;
        }
        String groupId = plugin.getGroupId().getStringValue();
        StringBuilder sb = new StringBuilder();
        if (groupId == null) {
            sb.append(artifactId);
        } else {
            sb.append(groupId).append(PART_SEPARATOR).append(artifactId);
        }
        appendPartIfNotNull(sb, plugin.getVersion().getStringValue());
        boolean hasMore = hasSubTagsExcept(plugin, Set.of("groupId", "artifactId", "version"));
        if (hasMore) {
            sb.append(MORE_ENDING);
        }
        return sb.toString();
    }

    private static String describeExtension(MavenDomExtension extension) {
        String artifactId = extension.getArtifactId().getStringValue();
        if (artifactId == null) {
            return null;
        }
        String groupId = extension.getGroupId().getStringValue();
        StringBuilder sb = new StringBuilder();
        if (groupId == null) {
            sb.append(artifactId);
        } else {
            sb.append(groupId).append(PART_SEPARATOR).append(artifactId);
        }
        appendPartIfNotNull(sb, extension.getVersion().getStringValue());
        return sb.toString();
    }

    private static String describeExecution(MavenDomPluginExecution execution) {
        String id = execution.getId().getStringValue();
        if (id == null) {
            return null;
        }
        boolean hasMore = hasSubTagsExcept(execution, Set.of("id"));
        return hasMore ? id + MORE_ENDING : id;
    }

    private static void appendPartIfNotNull(StringBuilder sb, String s) {
        if (s != null) {
            sb.append(PART_SEPARATOR).append(s);
        }
    }

    private static boolean hasSubTagsExcept(DomElement element, Set<String> expectedTags) {
        XmlTag xmlTag = element.getXmlTag();
        if (xmlTag != null) {
            for (XmlTag subTag : xmlTag.getSubTags()) {
                if (!expectedTags.contains(subTag.getLocalName())) {
                    return true;
                }
            }
        }
        return false;
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
