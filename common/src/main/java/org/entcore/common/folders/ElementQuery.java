package org.entcore.common.folders;

import java.util.HashMap;
import java.util.Map;

public class ElementQuery {
	private String searchByName;
	private Boolean hierarchical;
	private Boolean trash;
	private String idFolder;
	private String application;
	private Map<String, Object> params = new HashMap<String, Object>();

	public String getApplication() {
		return application;
	}

	public Map<String, Object> getParams() {
		return params;
	}

	public void setApplication(String application) {
		this.application = application;
	}

	public void setParams(Map<String, Object> params) {
		this.params = params;
	}

	public void setSearchByName(String searchByName) {
		this.searchByName = searchByName;
	}

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
