package com.logicgate.farm.util;

import java.util.Comparator;
import java.util.List;

public class ReverseListSizeComparator implements Comparator<List<?>> {

  @Override
  public int compare(List<?> list1, List<?> list2) {
    return Integer.compare(list2.size(), list1.size());
  }
}
