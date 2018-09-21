package org.entcore.common.folders;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ElementQuery {
	public static enum ElementSort {
		Asc, Desc
	}

	private String id;
	private Integer type;
	private Integer skip;
	private Integer limit;
	private boolean shared;
	private Boolean trash;
	private String parentId;
	private String application;
	private String searchByName;
	private Boolean hierarchical;
	private Collection<String> ids;
	private List<String> projection;
	private Set<String> visibilities;
	private List<String> fullTextSearch;
	private List<Map.Entry<String, ElementSort>> sort;
	private Map<String, Object> params = new HashMap<String, Object>();

	public ElementQuery(boolean includeShared) {
		this.shared = includeShared;
	}

	public boolean getShared() {
		return shared;
	}

	public void setShared(boolean shared) {
		this.shared = shared;
	}

	public Collection<String> getIds() {
		return ids;
	}

	public void setIds(Collection<String> ids) {
		this.ids = ids;
	}

	public Integer getType() {
		return type;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public Integer getLimit() {
		return limit;
	}

	public Integer getSkip() {
		return skip;
	}

	public void setLimit(Integer limit) {
		this.limit = limit;
	}

	public void setSkip(Integer skip) {
		this.skip = skip;
	}

	public void addSort(String name, ElementSort sort) {
		if (this.sort == null) {
			this.sort = new ArrayList<>();
		}
		this.sort.add(new AbstractMap.SimpleEntry<String, ElementSort>(name, sort));
	}

	public void addProjection(String name) {
		if (this.projection == null) {
			this.projection = new ArrayList<>();
		}
		this.projection.add(name);
	}

	public List<String> getProjection() {
		return projection;
	}

	public List<Map.Entry<String, ElementSort>> getSort() {
		return sort;
	}

	public void setSort(List<Map.Entry<String, ElementSort>> sort) {
		this.sort = sort;
	}

	public List<String> getFullTextSearch() {
		return fullTextSearch;
	}

	public void setFullTextSearch(List<String> fullTextSearch) {
		this.fullTextSearch = fullTextSearch;
	}

	public void setProjection(List<String> projection) {
		this.projection = projection;
	}

	public Set<String> getVisibilities() {
		return visibilities;
	}

	public void setVisibilities(Set<String> visibilities) {
		this.visibilities = visibilities;
	}

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

	public String getId() {
		return id;
	}

	public String getParentId() {
		return parentId;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}
}
