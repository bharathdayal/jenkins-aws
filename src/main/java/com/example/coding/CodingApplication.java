package com.example.coding;

import com.example.coding.design.DsaCode;
import com.example.coding.design.FactoryDSA;
import com.example.coding.design.SingletonLogger;
import org.hibernate.boot.model.source.internal.hbm.Helper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SpringBootApplication
public class CodingApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodingApplication.class, args);

		SingletonLogger logger = SingletonLogger.getInstance();

		DsaCode palindrome = FactoryDSA.getMethod("Palindrome");
		palindrome.dsaChallenge("malayalam");

		DsaCode arrayNonZero = FactoryDSA.getMethod("ArrayNonZero");
		arrayNonZero.dsaChallenge();

		DsaCode longSubString = FactoryDSA.getMethod("LongSubString");
		longSubString.dsaChallenge("abcabcbb");


		//STREAMS
		//Even number - Int
		int[] intArr = {1,2,3,4,5,6,7,8,9,10};
		IntStream intArrResult = IntStream.of(intArr);
		intArrResult.filter(n->n%2==0).boxed().forEach(m->logger.message("Even Number from INT====> "+m));

		//Even number - List
		List<Integer> intList = List.of(1,2,3,4,5,6,7,8,9,10);
		List<Integer> intListResult = intList.stream().filter(n->n%2==0).toList();
		logger.message("Even Number from LIST====> "+intListResult);

		//Square number - List
		List<Integer> intSquare = List.of(1,2,3,4,5);
		List<Integer> intSquareResult = intSquare.stream().map(n->n *n).toList();
		logger.message("Square of Number====> "+intSquareResult);

		//Square Even number - List
		List<Integer> intSquareEvenResult = intSquare.stream().filter(n->n%2==0).map(m->m*m).toList();
		logger.message("Square Even Number====> "+intSquareEvenResult);

		//First number Greater than 10- List
		List<Integer> intGreatList = List.of(3,7,12,5,11,20);
		Optional<Integer> intGreatResult = intGreatList.stream().filter(n->n >=10).sorted().findFirst();
		logger.message("First number Greater than 10====> "+intGreatResult);

		//Count number Greater than 5- List
		List<Integer> intCountList = List.of(2,6,3,8,10,1,12);
		Long intCountResult = intCountList.stream().filter(n->n>=5).count();
		logger.message("Count number Greater than 5====> "+intCountResult);

		//Find Sum- List
		List<Integer> intSumList = List.of(1,2,3,4,5,20);
		List<Integer> intSumResult = intSumList.stream().reduce(Integer::sum).stream().toList();
		logger.message("Find Sum of Numbers====> "+intSumResult);

		//Find Sum of Even Number- List
		List<Integer> intSumEvenResult = intSumList.stream().filter(n->n%2==0).reduce(Integer::sum).stream().toList();
		logger.message("Find Sum of Even Numbers====> "+intSumEvenResult);

		//Find Max Number- List
		List<Integer> intMaxResult = intSumList.stream().max(Comparator.comparing(Integer::intValue)).stream().toList();
		logger.message("Find Max====> "+intMaxResult);

		//Find Sum of Square of Even Number- List
		List<Integer> intSumSquareEvenList = List.of(1,2,3,4,5,6,7,8,9,10);
		List<Integer> intSumSquareEvenResult = intSumSquareEvenList.stream().filter(n->n%2==0).map(m->m*m).reduce(Integer::sum).stream().toList();
		logger.message("Sum of Square of Even Number====> "+intSumSquareEvenResult);

        //Test code
		List<String> freq = List.of("Apple","Banana","Orange","Apple");
		Map<String,Long> freqResult = freq.stream()
				.collect(Collectors.groupingBy(
						c->c,
						Collectors.counting()
				));

		logger.message(freqResult.toString());

		String mapStr = "Banana";
		HashMap<Character,Long> hashMap = new HashMap<>();
		for(char ch: mapStr.toCharArray()) {
			hashMap.put(ch,hashMap.getOrDefault(ch,0L)+1);
		}
		logger.message(hashMap.toString());

		HashMap<String,Long> hashMap1 = new HashMap<>();
		for(String ch1: freq) {
			hashMap1.put(ch1,hashMap1.getOrDefault(ch1,0L)+1);
		}

		logger.message(hashMap1.toString());



		Map<Character,Long> freqResult1 = mapStr.chars()
				.mapToObj(c->(char) c)
				.collect(Collectors.groupingBy(
						c->c,
						Collectors.counting()
				));


		logger.message(freqResult1.toString());

		List<Character> charList = List.of('a','b','1','2');
		Map<Boolean,List<Character>> charMap = charList.stream().collect(Collectors.partitioningBy(
				Character::isDigit));

		logger.message(charMap.toString());


	}



}
