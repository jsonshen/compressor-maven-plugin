/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.shenjia.mavenplugins.compressor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.IOUtil;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;

@Mojo(name = "precompress", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class PrecompressMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}/classes", readonly = true, required = true)
    private File inputDirectory;

    @Parameter(defaultValue = "${project.build.outputDirectory}/classes", readonly = true, required = true)
    private File outputDirectory;

    @Parameter(defaultValue = ".css,.js,.svg,.txt,.md,.html,.xml,.json", readonly = true, required = true)
    private String[] includeSuffixes;

    @Parameter(defaultValue = "GZIP,BROTLI", readonly = true, required = true)
    private Algorithm[] algorithms;

    @Parameter(defaultValue = "2", readonly = true, required = true)
    private long minResponseSize;

    public void setInputDirectory(File inputDirectory) {
		this.inputDirectory = inputDirectory;
	}

	public void setOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	public void setIncludeSuffixes(String[] includeSuffixes) {
		this.includeSuffixes = includeSuffixes;
	}

	public void setAlgorithms(Algorithm[] algorithms) {
		this.algorithms = algorithms;
	}

	public void setMinResponseSize(long minResponseSize) {
		this.minResponseSize = minResponseSize;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
        for (Algorithm alg : algorithms) {
            if (alg == Algorithm.BROTLI) {
                Brotli4jLoader.ensureAvailability();
            }
        }
        compress(inputDirectory, inputDirectory.getPath().length());
    }

    private void compress(File file, int beginIndex) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                compress(f, beginIndex);
            }
        }
        if (file.isFile() && isInclude(file)) {
            String subPath = file.getPath().substring(beginIndex);
            for (Algorithm alg : algorithms) {
                File dest = null;
                if (alg == Algorithm.GZIP) {
                    dest = gzip(file, subPath);
                } else if (alg == Algorithm.BROTLI) {
                    dest = brotli(file, subPath);
                }
                if (null != dest) {
                    long fileLen = file.length();
                    long destLen = dest.length();
                    long percent = destLen * 100 / fileLen;
                    if (percent >= 100) {
                        dest.delete();
                        return;
                    }
                    String format = "%s(%sb) -> %s(%sb)[%s]";
                    String msg = String.format(format, file.getName(), fileLen, dest.getName(), destLen, percent + "%");
                    getLog().info(msg);
                }
            }
        }
    }

    private boolean isInclude(File file) {
        long len = file.length();
        if (len <= 0 || len < minResponseSize) {
            return false;
        }
        for (String suffix : includeSuffixes) {
            if (file.getName().endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private File gzip(File file, String subPath) {
        File dest = new File(outputDirectory, subPath + ".gz");
        try (InputStream in = new FileInputStream(file);
            OutputStream out = new GZIPOutputStream(new FileOutputStream(dest))) {
            IOUtil.copy(in, out);
        } catch (IOException e) {
            getLog().error(e);
            return null;
        }
        return dest;
    }

    private File brotli(File file, String subPath) {
        File dest = new File(outputDirectory, subPath + ".br");
        try (InputStream in = new FileInputStream(file);
            OutputStream out = new BrotliOutputStream(new FileOutputStream(dest))) {
            IOUtil.copy(in, out);
        } catch (IOException e) {
            getLog().error(e);
            return null;
        }
        return dest;
    }
}
