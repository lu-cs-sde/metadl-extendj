package org.extendj;

import java.util.Stack;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import org.extendj.ast.ASTNode;
import org.extendj.ast.ASTState;

interface StackEntry {
	public Object execute(Stack<StackEntry> stack);
}

class AttributeValue {
	private ASTNode n;
	private String attribute;
	private Object params;

	public AttributeValue(ASTNode n, String attribute, Object params) {
		this.n = n;
		this.attribute = attribute;
		this.params = params;
	}

	@Override public boolean equals(Object other) {
		if (!(other instanceof AttributeValue))
			return false;
		AttributeValue attr = (AttributeValue) other;
		return n.equals(attr.n)
			&& attribute.equals(attr.attribute)
			&& params.equals(attr.params);
	}

	@Override public int hashCode() {
		int m = 31;
		int r = n.hashCode();
		r = r * 31 + attribute.hashCode();
		if (params != null) {
			r = r * 31 + params.hashCode();
		}
		return r;
	}
}

class AttributeBegin implements StackEntry {
	private AttributeValue attr;
	public AttributeBegin(AttributeValue attr) {
		this.attr = attr;
	}

	public AttributeValue getAttributeValue() {
		return attr;
	}

	@Override public Object execute(Stack<StackEntry> stack) {
		// do nothing
		return null;
	}
}

class AttributeEnd implements StackEntry {
	private Map<AttributeValue, Set<String>> attrFileDeps;
	private AttributeValue attr;
	public AttributeEnd(AttributeValue attr, Map<AttributeValue, Set<String>> attrFileDeps) {
		this.attrFileDeps = attrFileDeps;
		this.attr = attr;
	}

	@Override public Object execute(Stack<StackEntry> stack) {
		StackEntry top = stack.pop();
		assert top == this;

		Set<String> fileList = attrFileDeps.get(attr);
		if (fileList == null) {
			fileList = new HashSet<String>();
			attrFileDeps.put(attr, fileList);
		}

		do {
			if (stack.peek() instanceof FileRead) {
				top = stack.pop();
				fileList.add(((FileRead) top).getFile());
			} else if (stack.peek() instanceof FileSet) {
				top = stack.pop();
				fileList.addAll(((FileSet) top).getFiles());
			} else if (stack.peek() instanceof AttributeBegin) {
				top = stack.pop();
				assert ((AttributeBegin) top).getAttributeValue().equals(attr);
				stack.push(new FileSet(fileList));
				break;
			} else {
				throw new RuntimeException("Unexpected entry on top of the stack: " + stack.peek());
			}
		} while (!stack.empty());

		return null;
	}
}

class FileSet implements StackEntry {
	private Set<String> fileSet;
	public FileSet(Set<String> fileSet) {
		this.fileSet = fileSet;
	}

	@Override public Object execute(Stack<StackEntry> stack) {
		return null;
	}

	public Set<String> getFiles() {
		return fileSet;
	}
}

class FileRead implements StackEntry {
	private String file;
	public FileRead(String file) {
		this.file = file;
	}

	@Override public Object execute(Stack<StackEntry> stack) {
		return null;
	}

	String getFile() {
		return file;
	}
}

public class ProvenanceStackMachine implements ASTState.Trace.Receiver {
	private Stack<StackEntry> stack = new Stack<StackEntry>();
	private Map<AttributeValue, Set<String>> attrToFile = new HashMap<AttributeValue, Set<String>>();

	private void addAndExecute(StackEntry e) {
		stack.push(e);
		e.execute(stack);
	}

	public void fileRead(String path) {
		addAndExecute(new FileRead(path));
	}

	public void attributeBegin(ASTNode node, String attribute, Object params) {
		addAndExecute(new AttributeBegin(new AttributeValue(node, attribute, params)));
	}

	public void attributeEnd(ASTNode node, String attribute, Object params) {
		addAndExecute(new AttributeEnd(new AttributeValue(node, attribute, params), attrToFile));
	}

	@Override
	public void accept(ASTState.Trace.Event event, ASTNode node, String attribute,
					   Object params, Object value) {
		switch (event) {
		case COMPUTE_BEGIN: {
			attributeBegin(node, attribute, params);
			break;
		}
		case COMPUTE_END: {
			attributeEnd(node, attribute, params);
			break;
		}
		}
	}
}
