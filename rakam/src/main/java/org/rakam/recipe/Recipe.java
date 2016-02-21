package org.rakam.recipe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.rakam.collection.FieldType;
import org.rakam.collection.SchemaField;
import org.rakam.plugin.ContinuousQuery;
import org.rakam.plugin.MaterializedView;
import org.rakam.plugin.ProjectItem;
import org.rakam.ui.page.CustomPageDatabase;
import org.rakam.ui.customreport.CustomReport;
import org.rakam.ui.report.Report;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class Recipe implements ProjectItem {
    private final Strategy strategy;
    private final String project;
    private final Map<String, Collection> collections;
    private final List<MaterializedViewBuilder> materializedViews;
    private final List<ContinuousQueryBuilder> continuousQueries;
    private final List<ReportBuilder> reports;
    private final List<CustomReportBuilder> customReports;
    private final List<CustomPageBuilder> customPages;

    @JsonCreator
    public Recipe(@JsonProperty("strategy") Strategy strategy,
                  @JsonProperty("project") String project,
                  @JsonProperty("collections") Map<String, Collection> collections,
                  @JsonProperty("materialized_views") List<MaterializedViewBuilder> materializedQueries,
                  @JsonProperty("continuous_queries") List<ContinuousQueryBuilder> continuousQueries,
                  @JsonProperty("custom_reports") List<CustomReportBuilder> customReports,
                  @JsonProperty("custom_pages") List<CustomPageBuilder> customPages,
                  @JsonProperty("reports") List<ReportBuilder> reports) {
        if (strategy != Strategy.SPECIFIC && project != null) {
            throw new IllegalArgumentException("'project' parameter can be used when 'strategy' is 'specific'");
        }
        this.strategy = strategy;
        this.project = project;
        this.collections = collections != null ? ImmutableMap.copyOf(collections) : ImmutableMap.of();
        this.customReports = customReports == null ? ImmutableList.of() : customReports;
        this.customPages = customPages == null ? ImmutableList.of() : customPages;
        this.materializedViews = materializedQueries == null ? ImmutableList.of() : ImmutableList.copyOf(materializedQueries);
        this.continuousQueries = continuousQueries == null ? ImmutableList.of() : ImmutableList.copyOf(continuousQueries);
        this.reports = reports == null ? ImmutableList.of() : ImmutableList.copyOf(reports);
    }

    @JsonProperty("strategy")
    public Strategy getStrategy() {
        return strategy;
    }

    @JsonProperty("project")
    public String getProject() {
        return project;
    }

    @JsonProperty("custom_pages")
    public List<CustomPageBuilder> getCustomPages() {
        return customPages;
    }

    @JsonProperty("custom_reports")
    public List<CustomReportBuilder> getCustomReports() {
        return customReports;
    }

    @JsonProperty("collections")
    public Map<String, Collection> getCollections() {
        return collections;
    }

    @JsonProperty("materialized_views")
    public List<MaterializedViewBuilder> getMaterializedViewBuilders() {
        return materializedViews;
    }

    @JsonProperty("continuous_queries")
    public List<ContinuousQueryBuilder> getContinuousQueryBuilders() {
        return continuousQueries;
    }

    @JsonProperty("reports")
    public List<ReportBuilder> getReports() {
        return reports;
    }

    @Override
    @JsonIgnore
    public String project() {
        return project;
    }

    public static class Collection {
        public final List<Map<String, SchemaFieldInfo>> columns;

        @JsonCreator
        public Collection(@JsonProperty("columns") List<Map<String, SchemaFieldInfo>> columns) {
            this.columns = columns;
        }

        @JsonIgnore
        public List<SchemaField> build() {
            return columns.stream()
                    .map(column -> {
                        Map.Entry<String, SchemaFieldInfo> next = column.entrySet().iterator().next();
                        return new SchemaField(next.getKey(), next.getValue().type);
                    }).collect(Collectors.toList());
        }
    }

    public static class MaterializedViewBuilder {
        public final String name;
        public final String table_name;
        public final String query;
        public final String incremental_field;
        public final Duration updateInterval;

        @Inject
        public MaterializedViewBuilder(@JsonProperty("name") String name, @JsonProperty("table_name") String table_name, @JsonProperty("query") String query, @JsonProperty("update_interval") Duration updateInterval, @JsonProperty("incremental_field") String incremental_field) {
            this.name = name;
            this.table_name = table_name;
            this.query = query;
            this.incremental_field = incremental_field;
            this.updateInterval = updateInterval;
        }

        public MaterializedView createMaterializedView(String project) {
            return new MaterializedView(project, name, table_name, query, updateInterval, incremental_field);
        }
    }

    public static class ContinuousQueryBuilder {
        public final String name;
        public final String tableName;
        public final String query;
        public final List<String> partitionKeys;
        public final Map<String, Object> options;

        @JsonCreator
        public ContinuousQueryBuilder(@JsonProperty("name") String name,
                                      @JsonProperty("table_name") String tableName,
                                      @JsonProperty("query") String query,
                                      @JsonProperty("partition_keys") List<String> partitionKeys,
                                      @JsonProperty("options") Map<String, Object> options) {
            this.name = name;
            this.tableName = tableName;
            this.query = query;
            this.partitionKeys = partitionKeys;
            this.options = options;
        }

        public ContinuousQuery createContinuousQuery(String project) {
            return new ContinuousQuery(project, name, tableName, query, partitionKeys, options);
        }
    }

    public static class ReportBuilder {
        public final String slug;
        public final String name;
        public final String query;
        public final Map<String, Object> options;
        public final String category;

        @JsonCreator
        public ReportBuilder(@JsonProperty("slug") String slug,
                             @JsonProperty("name") String name,
                             @JsonProperty("category") String category,
                             @JsonProperty("query") String query,
                             @JsonProperty("options") Map<String, Object> options) {
            this.slug = slug;
            this.category = category;
            this.name = name;
            this.query = query;
            this.options = options;
        }

        public Report createReport(String project) {
            return new Report(project, slug, category, name, query, options);
        }
    }

    public static class CustomReportBuilder {
        public final String reportType;
        public final String name;
        public final Object data;

        @JsonCreator
        public CustomReportBuilder(
                @JsonProperty("report_type") String reportType,
                @JsonProperty("name") String name,
                @JsonProperty("data") Object data) {
            this.reportType = reportType;
            this.name = name;
            this.data = data;
        }

        public CustomReport createCustomReport(String project) {
            return new CustomReport(reportType, project, name, data);
        }
    }

    public static class CustomPageBuilder {
        public final String slug;
        public final String name;
        public final String category;
        public final Map<String, String> files;

        @JsonCreator
        public CustomPageBuilder(
                @JsonProperty("slug") String slug,
                @JsonProperty("name") String name,
                @JsonProperty("category") String category,
                @JsonProperty("files") Map<String, String> files) {
            this.slug = slug;
            this.name = name;
            this.category = category;
            this.files = checkNotNull(files, "files");
        }

        public CustomPageDatabase.Page createCustomPage(String project) {
            return new CustomPageDatabase.Page(project, name, slug, category, files);
        }
    }

    public static class SchemaFieldInfo {
        public final String category;
        public final FieldType type;

        @JsonCreator
        public SchemaFieldInfo(@JsonProperty("category") String category,
                               @JsonProperty("type") FieldType type) {
            this.category = category;
            this.type = type;
        }
    }

    public enum Strategy {
        DEFAULT, SPECIFIC;

        @JsonCreator
        public static Strategy get(String name) {
            return valueOf(name.toUpperCase());
        }
    }
}