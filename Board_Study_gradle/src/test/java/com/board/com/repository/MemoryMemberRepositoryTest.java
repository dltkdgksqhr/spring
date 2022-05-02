package com.board.com.repository;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.board.com.domain.Member;



class MemoryMemberRepositoryTest {
	
	MemoryMemberRepository repository = new MemoryMemberRepository();
	
	@AfterEach
	public void afterEach() {
		repository.clearStore();
	}
	
	@Test
	public void save() {
		Member member = new Member();
		member.setName("spring");
		
		repository.save(member);
		
		Member result = repository.findById(member.getId()).get();
		//Assertions.assertEquals(result, member); //result값이랑 member 값이랑 같은지 확인할 수 있습니다.
		Assertions.assertThat(member).isEqualTo(result);
	}
	
	@Test
	public void findByName() {
		Member member1 = new Member();
		member1.setName("spring1");
		repository.save(member1);
		
		Member member2 = new Member();
		member2.setName("spring2");
		repository.save(member2);
		
		Member result= repository.findByName("spring1").get();
		Assertions.assertThat(result).isEqualTo(member1);
	}

	@Test
	public void findAll() {
	  Member member1 = new Member();
	  member1.setName("spring1");
	  repository.save(member1);
	  
	  Member member2 = new Member();
	  member2.setName("spring2");
	  repository.save(member2);
	  
	  List<Member> result = repository.findAll();
	  
	  Assertions.assertThat(result.size()).isEqualTo(2);
	  
	}
}
