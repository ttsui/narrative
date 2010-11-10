/* Based on http://www.antlr.org/wiki/display/ANTLR3/Tool+showing+grammatical+structure+of+Java+code */

/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenRewriteStream;

public class TestParser {
    Stack<TreeNode> treeStack = new Stack<TreeNode>();
    TreeNode currentParent;
    
    public TestParser() { }

    public TreeNode parse(String fileName) throws IOException {
        String fileContent = slurpFile(fileName);
        return buildTree(fileContent);
    }

    private String slurpFile(String fileName) throws IOException {
        FileInputStream stream = new FileInputStream(new File(fileName));
        try {
            FileChannel fc = stream.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            return Charset.defaultCharset().decode(bb).toString();
        }
        finally {
            stream.close();
        }
    }


    /**
     * Set the current parent of the node. Any node added after this call will be 
     * counted as children of the newParent.
     * 
     * @param newParent
     * @return
     */
    public TreeNode setCurrentParent(TreeNode newParent) {
        TreeNode oldParent = currentParent;
        currentParent = newParent;
        return oldParent;
    }

    public TreeNode getCurrentParent() {
        return currentParent;
    }

    /**
     * Add a child to the currentParent set by calling setCurrentParent().
     * @param name
     * @return
     */
    public TreeNode addNode(String name, Token start, Token stop) {
        TreeNode node = new TreeNode(name);
        if (currentParent != null)
            currentParent.add(node);
        node.setLeaf(false);
        return node;
    }

    public TreeNode addNode(String name) {
        TreeNode node = new TreeNode(name);
        node.setLeaf(false);
        if (currentParent != null)
            currentParent.add(node);

        return node;
    }

    /**
     * Same as addNode(), except that a leaf should contain no child.
     * @param name
     * @return
     */
    public TreeNode addLeaf(String name, Token token) {
        TreeNode node = new TreeNode(name);
        node.setLeaf(true);
        if (currentParent != null)
            currentParent.add(node);
        return node;
    }

    /**
     * Stores the current parent in the stack, this is usually called before setting a new parent.
     */
    public void pushTop() {
        treeStack.push(currentParent);
    }

    /**
     * Restores the previous parent.
     */
    public TreeNode popTop() {
        TreeNode ret = currentParent;
        currentParent = treeStack.pop();
        return ret;
    }

    private TreeNode buildTree(String fileContent) {
        TreeNode root = new TreeNode("compilationUnit");
        try {
            JavaLexer lex = new JavaLexer(new ANTLRStringStream(fileContent));
            TokenRewriteStream tokens = new TokenRewriteStream(lex);
            JavaParser g = new JavaParser(tokens);
            g.setTestParser(this);
            this.setCurrentParent(root);
            g.compilationUnit();                        
            if (g.getNumberOfSyntaxErrors() != 0) {
                throw new IllegalStateException("There are syntax errors in the input file.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("There are syntax errors in input file.");
        }
        return root;
    }
}

class TreeNode {
    private static final long serialVersionUID = 1L;
    private static Pattern literalPattern = Pattern.compile(".*\\[['\"](.*)['\"]\\]");
    String text;
    private boolean isLeaf = false;
    List<TreeNode> children = new ArrayList<TreeNode>();

    public TreeNode(String text) {
        this.text = text;
    }

    public void add(TreeNode node) {
        children.add(node);
    }

    public void setLeaf(boolean isLeaf) {
        this.isLeaf = isLeaf;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public boolean isLiteral() {
        return literalPattern.matcher(text).matches();
    }

    public TreeNode getFirstChildByName(String name) {
        for (TreeNode child : children) {
            if (child.text.equals(name)) { return child; }
        }
        return null;
    }

    public boolean isTestMethod() {
        if (! text.equals("methodDeclaration")) { return false; }
        TreeNode modifiers      = this.         getFirstChildByName("modifiers");          if (null == modifiers)      { return false; }
        TreeNode annotation     = modifiers.    getFirstChildByName("annotation");         if (null == annotation)     { return false; }
        TreeNode qualifiedName  = annotation.   getFirstChildByName("qualifiedName");      if (null == qualifiedName)  { return false; }
        TreeNode testAnnotation = qualifiedName.getFirstChildByName("IDENTIFIER['Test']"); if (null == testAnnotation) { return false; }
        return true;
    }
    
    public String toString() { 
        Matcher literalMatcher = literalPattern.matcher(text);
        if (literalMatcher.matches()) { return literalMatcher.group(1); } else { return text; }
    }
}
