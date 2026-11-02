package org.example.db;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name="kwork")
public class KworkEntity {
    @Id
    @Column(name = "kwork_order_id")
    private Long id;
    @Column(name = "kwork_name")
    private String name;
    @Column(name = "kwork_description", columnDefinition = "TEXT")
    private String description;
    @Column(name = "kwork_price_limit")
    private BigDecimal priceLimit;
    @Column(name = "category_id")
    private Long categoryId;
    @Column(name = "max_days")
    private Integer maxDays;
    @Column(name = "creation_date")
    private LocalDateTime dateCreate;
    @Column(name = "username")
    private String username;
    @Column(name = "user_profile")
    private String userProfileUrl;
    @Column(name = "kwork_count")
    private int kworkCount;

    // Служебные поля жизненного цикла заказа. Пишутся только SQL-upsert'ом в KworkRepository
    // (значения now() берутся с часов БД), поэтому Hibernate их не вставляет и не обновляет.
    @Column(name = "first_seen_at", columnDefinition = "timestamptz default now()", insertable = false, updatable = false)
    private Instant firstSeenAt;
    @Column(name = "last_seen_at", columnDefinition = "timestamptz default now()", insertable = false, updatable = false)
    private Instant lastSeenAt;
    /** false — заказ пропал с сайта; бот такие заказы не рассылает. */
    @Column(name = "is_active", nullable = false, columnDefinition = "boolean not null default true", insertable = false, updatable = false)
    private boolean active = true;
    @Column(name = "closed_at", columnDefinition = "timestamptz", insertable = false, updatable = false)
    private Instant closedAt;
    /** Время публикации заказа на бирже по данным сайта (МСК, без пояса); меняется, когда заказчик поднимает заказ. */
    @Column(name = "date_active", columnDefinition = "timestamp")
    private LocalDateTime dateActive;
    /**
     * Когда заказ «появился» для рассылки: первое появление у парсера или момент, когда изменился date_active.
     * По нему бот выбирает новые заказы: id не годится, т.к. заказы публикуются после модерации не по порядку id
     * и старые заказы поднимаются заново.
     */
    @Column(name = "activated_at", columnDefinition = "timestamptz default now()", insertable = false, updatable = false)
    private Instant activatedAt;

    public KworkEntity(){

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPriceLimit() {
        return priceLimit;
    }

    public void setPriceLimit(BigDecimal priceLimit) {
        this.priceLimit = priceLimit;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getMaxDays() {
        return maxDays;
    }

    public void setMaxDays(Integer maxDays) {
        this.maxDays = maxDays;
    }

    public LocalDateTime getDateCreate() {
        return dateCreate;
    }

    public void setDateCreate(LocalDateTime dateCreate) {
        this.dateCreate = dateCreate;
    }

    public String getUserProfileUrl() {
        return userProfileUrl;
    }

    public void setUserProfileUrl(String userProfileUrl) {
        this.userProfileUrl = userProfileUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getKworkCount() {
        return kworkCount;
    }

    public void setKworkCount(int kworkCount) {
        this.kworkCount = kworkCount;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public LocalDateTime getDateActive() {
        return dateActive;
    }

    public void setDateActive(LocalDateTime dateActive) {
        this.dateActive = dateActive;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    @Override
    public String toString() {
        return "KworkEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", priceLimit=" + priceLimit +
                ", categoryId=" + categoryId +
                ", maxDays=" + maxDays +
                ", dateCreate=" + dateCreate +
                ", username='" + username + '\'' +
                ", userProfileUrl='" + userProfileUrl + '\'' +
                ", kworkCount=" + kworkCount +
                '}';
    }
}
