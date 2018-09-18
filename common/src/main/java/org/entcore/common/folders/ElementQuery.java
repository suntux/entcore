package org.entcore.common.folders;

public class ElementQuery {
	private String searchByName;
	private Boolean hierarchical;
	private Boolean trash;
	private String idFolder;

	public String getSearchByName() {
		return searchByName;
	}

	public void setFolder(String folder) {
		this.searchByName = folder;
	}

	public Boolean getHierarchical() {
		return hierarchical;
	}

	public void setHierarchical(Boolean hierarchical) {
		this.hierarchical = hierarchical;
	}

	public Boolean getTrash() {
		return trash;
	}

	public void setTrash(Boolean trash) {
		this.trash = trash;
	}

	public String getIdFolder() {
		return idFolder;
	}

	public void setIdFolder(String idFolder) {
		this.idFolder = idFolder;
	}

}
