package com.fuiny.mavendb;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;
import org.eclipse.persistence.annotations.Cache;
import org.eclipse.persistence.annotations.CacheType;

/**
 * JPA Persistent class for table <code>artifactinfo</code>.
 */
@Entity
@Table(name = "artifactinfo")
@Cache(type = CacheType.NONE)  // Does not preserve object identity and does not cache objects.
@NamedQueries({
    @NamedQuery(name = "Artifactinfo.findAll", query = "SELECT a FROM Artifactinfo a")
    , @NamedQuery(name = "Artifactinfo.findByUinfoMd5", query = "SELECT a FROM Artifactinfo a WHERE a.uinfoMd5 = :uinfoMd5")  // Only select one column to speed up
    , @NamedQuery(name = "Artifactinfo.findByMajorVersion", query = "SELECT a FROM Artifactinfo a WHERE a.majorVersion = :majorVersion")
})
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class Artifactinfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Basic(optional = false)
    @Column(name = "uinfo_md5")
    private byte[] uinfoMd5;

    @Column(name = "major_version")
    private Integer majorVersion;
    @Column(name = "version_seq")
    private BigInteger versionSeq;
    @Column(name = "uinfo_length")
    private Integer uinfoLength;
    @Column(name = "classifier_length")
    private Integer classifierLength;

    @Column(name = "signature_exists")
    private Integer signatureExists;
    @Column(name = "sources_exists")
    private Integer sourcesExists;
    @Column(name = "javadoc_exists")
    private Integer javadocExists;

    /**
     * We treat MySQL JSON data type as String.
     */
    @Column(name = "json")
    private String json;

    public Artifactinfo() {
    }

    public Artifactinfo(byte[] md5) {
        this.uinfoMd5 = md5;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (this.uinfoMd5 != null ? Arrays.hashCode(this.uinfoMd5) : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Artifactinfo)) {
            return false;
        }
        Artifactinfo other = (Artifactinfo) object;
        return (other.uinfoMd5 != null) && Arrays.equals(other.uinfoMd5, this.uinfoMd5);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[ uinfo=" + Hex.encodeHexString(this.uinfoMd5) + " ]";
    }

    @SuppressFBWarnings(value="EI_EXPOSE_REP", justification="it is fine")
    public byte[] getUinfoMd5() {
        return uinfoMd5;
    }

    @SuppressFBWarnings(value="EI_EXPOSE_REP2", justification="it is fine")
    public void setUinfoMd5(byte[] uinfoMd5) {
        this.uinfoMd5 = uinfoMd5;
    }

    public Integer getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(Integer majorVersion) {
        this.majorVersion = majorVersion;
    }

    public BigInteger getVersionSeq() {
        return versionSeq;
    }

    public void setVersionSeq(BigInteger versionSeq) {
        this.versionSeq = versionSeq;
    }

    public Integer getUinfoLength() {
        return uinfoLength;
    }

    public void setUinfoLength(Integer uinfoLength) {
        this.uinfoLength = uinfoLength;
    }

    public Integer getClassifierLength() {
        return this.classifierLength;
    }

    public void setClassifierLength(Integer length) {
        this.classifierLength = length;
    }

    public Integer getSignatureExists() {
        return signatureExists;
    }

    public void setSignatureExists(Integer signatureExists) {
        this.signatureExists = signatureExists;
    }

    public Integer getSourcesExists() {
        return sourcesExists;
    }

    public void setSourcesExists(Integer sourcesExists) {
        this.sourcesExists = sourcesExists;
    }

    public Integer getJavadocExists() {
        return javadocExists;
    }

    public void setJavadocExists(Integer javadocExists) {
        this.javadocExists = javadocExists;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }


}
