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
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PomFoldingBuilder extends FoldingBuilderEx {
    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        if (!(root instanceof XmlFile)) {
            return FoldingDescriptor.EMPTY;
        }

        XmlTag rootTag = ((XmlFile) root).getRootTag();
        if (rootTag == null || !rootTag.getLocalName().equals("project")) {
            return FoldingDescriptor.EMPTY;
        }

        List<FoldingDescriptor> descriptors = new ArrayList<>();
        XmlTag dependenciesTag = rootTag.findFirstSubTag("dependencies");
        if (dependenciesTag != null) {
            XmlTag[] dependencyTags = dependenciesTag.findSubTags("dependency");
            for (XmlTag dependencyTag : dependencyTags) {
                XmlTag exclusionsTag = dependencyTag.findFirstSubTag("exclusions");
                if (exclusionsTag == null) {
//                        descriptors.add(new FoldingDescriptor(dependencyTag, dependencyTag.getTextRange()));
//                        TextRange rangeToFold = dependencyTag.getTextRange();
                    TextRange rangeToFold = getRangeToFold(dependencyTag);
                    if (rangeToFold != null) {
                        descriptors.add(new FoldingDescriptor(dependencyTag, rangeToFold) {
                            @Override
                            public String getPlaceholderText() {
                                return " " + describeDependency(dependencyTag);
                            }
                        });
                    }
                }
            }
        }
        return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
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

    private static String describeDependency(XmlTag dependencyTag) {
        //TODO type, classifier, scope, systemPath, exclusions, optional
        XmlTag groupIdTag = dependencyTag.findFirstSubTag("groupId");
        XmlTag artifactIdTag = dependencyTag.findFirstSubTag("artifactId");
        XmlTag versionTag = dependencyTag.findFirstSubTag("version");
        StringBuilder sb = new StringBuilder();
        if (groupIdTag != null) {
            sb.append(groupIdTag.getValue().getTrimmedText());
        }
        if (artifactIdTag != null) {
            sb.append(':');
            sb.append(artifactIdTag.getValue().getTrimmedText());
        }
        if (versionTag != null) {
            sb.append(':');
            sb.append(versionTag.getValue().getTrimmedText());
        }
        return sb.toString();
    }


    //TODO: it's a copypaste from com.intellij.lang.XmlCodeFoldingBuilder
    private static final TokenSet XML_ATTRIBUTE_SET = TokenSet.create(XmlElementType.XML_ATTRIBUTE);
    @Nullable
    private TextRange getRangeToFold(XmlTag xmlTag) {
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
