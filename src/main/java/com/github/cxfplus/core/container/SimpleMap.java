package com.github.cxfplus.core.container;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.apache.commons.lang.ObjectUtils;

/**
 * 用List实现的最简单的Map，目标是占用内存最小，不考虑性能，事实上元素不多的情况下性能不是什么问题。
 * 
 * 这个类更多的时候是用来作为JAXB的通用Map序列化。在我修改了JAXB的实现之后，这一序列化将泛型的map作为一个单独的类型来使用.
 * 
 * 值得注意的是，为了让JAXB序列化后不显示父类AbstractMap，这里特地进行了处理，将JDK的AbstractMap拷贝了一份出来，为了加上@XmlTransient这个标签
 * 。 因此这个类没有继承JDK的AbstractMap.
 * 
 * @param <K>
 * @param <V>
 */
@ObjectName("Map")
@XmlAccessorType(XmlAccessType.FIELD)
public class SimpleMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Serializable {
	private static final long serialVersionUID = -4930667933312037159L;

	@XmlElement(nillable = false, name = "entry")
	private List<com.github.cxfplus.core.container.Entry<K, V>> entries;

	public List<com.github.cxfplus.core.container.Entry<K, V>> getEntries() {
		return entries;
	}

	public void setEntries(List<com.github.cxfplus.core.container.Entry<K, V>> entries) {
		this.entries = entries;
	}

	public SimpleMap(com.github.cxfplus.core.container.Entry<K, V>[] entries) {
		if(entries==null){
			this.entries = new ArrayList<com.github.cxfplus.core.container.Entry<K,V>>();
		}else{
			this.entries = new ArrayList<com.github.cxfplus.core.container.Entry<K,V>>(Arrays.asList(entries));
		}
	}

	public SimpleMap() {
		this(16);
	}

	public SimpleMap(int size) {
		this.entries = new ArrayList<com.github.cxfplus.core.container.Entry<K, V>>(size);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public SimpleMap(Map<K, V> map) {
		if (map instanceof SimpleMap) {
			entries = ((SimpleMap) map).entries;
		} else {
			entries = new ArrayList<com.github.cxfplus.core.container.Entry<K, V>>(map.size());
			for (Entry<K, V> e : map.entrySet()) {
				entries.add(new com.github.cxfplus.core.container.Entry<K, V>(e.getKey(), e.getValue()));
			}
		}
	}

	/**
	 * 在Map中添加元素，不检查重复与否
	 * 
	 * @param key
	 * @param value
	 */
	public void add(K key, V value) {
		entries.add(new com.github.cxfplus.core.container.Entry<K, V>(key, value));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see AbstractMap#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V put(K key, V value) {
		int index = -1;
		for (int i = 0; i < entries.size(); i++) {
			if (ObjectUtils.equals(entries.get(i).getKey(), key)) {
				index = i;
				break;
			}
		}
		if (index > -1) {
			entries.set(index, new com.github.cxfplus.core.container.Entry<K, V>(key, value));
		} else {
			entries.add(new com.github.cxfplus.core.container.Entry<K, V>(key, value));
		}
		return value;
	}

	private class EntriesIterator implements Iterator<java.util.Map.Entry<K, V>> {
		private int n = 0;

		public boolean hasNext() {
			return n < entries.size();
		}

		public java.util.Map.Entry<K, V> next() {
			java.util.Map.Entry<K, V> result = entries.get(n);
			n++;
			return result;
		}

		public void remove() {
			n--;
			entries.remove(n);
		}
	}

	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return new AbstractSet<java.util.Map.Entry<K, V>>() {
			@Override
			public Iterator<java.util.Map.Entry<K, V>> iterator() {
				return new EntriesIterator();
			}

			@Override
			public int size() {
				return entries.size();
			}
		};

	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
	}

	@Override
	public Iterator<? extends java.util.Map.Entry<K, V>> entryIterator() {
		return entries.iterator();
	}

}
