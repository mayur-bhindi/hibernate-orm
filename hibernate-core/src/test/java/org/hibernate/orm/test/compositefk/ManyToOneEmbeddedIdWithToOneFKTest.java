/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.compositefk;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import org.hibernate.Hibernate;

import org.hibernate.testing.jdbc.SQLStatementInspector;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.FailureExpected;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertTrue;

/**
 * @author Andrea Boriero
 */
@DomainModel(
		annotatedClasses = {
				ManyToOneEmbeddedIdWithToOneFKTest.System.class,
				ManyToOneEmbeddedIdWithToOneFKTest.SystemUser.class,
				ManyToOneEmbeddedIdWithToOneFKTest.Subsystem.class
		}
)
@SessionFactory(statementInspectorClass = SQLStatementInspector.class)
public class ManyToOneEmbeddedIdWithToOneFKTest {

	@BeforeEach
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Subsystem subsystem = new Subsystem( 2, "sub1" );
					PK userKey = new PK( subsystem, "Fab" );
					SystemUser user = new SystemUser( userKey, "Fab" );

					System system = new System( 1, "sub1" );
					system.setUser( user );

					session.save( subsystem );
					session.save( user );
					session.save( system );
				}
		);
	}

	@AfterEach
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "delete from System" ).executeUpdate();
					session.createQuery( "delete from SystemUser" ).executeUpdate();
					session.createQuery( "delete from Subsystem" ).executeUpdate();
				}
		);
	}

	@Test
	public void testGet(SessionFactoryScope scope) {
		SQLStatementInspector statementInspector = (SQLStatementInspector) scope.getStatementInspector();
		statementInspector.clear();
		scope.inTransaction(
				session -> {
					System system = session.get( System.class, 1 );
					assertThat( system, is( notNullValue() ) );
					assertThat( system.getId() , is(1) );

					assertTrue( Hibernate.isInitialized( system.getUser() ) );

					PK pk = system.getUser().getPk();
					assertTrue( Hibernate.isInitialized( pk.subsystem ) );

					assertThat( pk.username, is( "Fab"));
					assertThat( pk.subsystem.id, is( 2));
					assertThat( pk.subsystem.getDescription(), is( "sub1"));

					SystemUser user = system.getUser();
					assertThat( user, is( notNullValue() ) );

					statementInspector.assertExecutedCount( 1 );
					statementInspector.assertNumberOfOccurrenceInQuery( 0, "join", 2 );
				}
		);
	}

	@Test
	public void testHql(SessionFactoryScope scope) {
		SQLStatementInspector statementInspector = (SQLStatementInspector) scope.getStatementInspector();
		statementInspector.clear();
		/*
		select
			s1_0.id_,
			s1_0.name as name2_1_,
			s1_0.user_subsystem_id ,
			s1_0.user_username_
		from
			System s1_0
		where
			s1_0.id=?

		 select
			s2_0.id,
			s2_0.description_
		from
			Subsystem s2_0
		where
			s2_0.id=?

		select
			manytoonee0_.subsystem_id as subsyste3_2_0_,
			manytoonee0_.username as username1_2_0_,
			manytoonee0_.name as name2_2_0_
		from
			SystemUser manytoonee0_
		where
			manytoonee0_.subsystem_id=?
			and manytoonee0_.username=?
		 */
		scope.inTransaction(
				session -> {
					System system = (System) session.createQuery( "from System e where e.id = :id" )
							.setParameter( "id", 1 ).uniqueResult();

					assertThat( system, is( notNullValue() ) );

					statementInspector.assertExecutedCount( 3 );
					statementInspector.assertNumberOfOccurrenceInQuery( 0, "join", 0 );
					statementInspector.assertNumberOfOccurrenceInQuery( 1, "join", 0 );
					statementInspector.assertNumberOfOccurrenceInQuery( 2, "join", 0 );


					assertTrue( Hibernate.isInitialized( system.getUser() ) );

					final PK pk = system.getUser().getPk();
					assertTrue( Hibernate.isInitialized( pk.subsystem ) );

					assertThat( pk.username, is( "Fab"));
					assertThat( pk.subsystem.id, is( 2));
					assertThat( pk.subsystem.getDescription(), is( "sub1"));

					SystemUser user = system.getUser();
					assertThat( user, is( notNullValue() ) );
					statementInspector.assertExecutedCount( 3 );
				}
		);
	}

	@Test
	public void testHqlJoin(SessionFactoryScope scope) {
		SQLStatementInspector statementInspector = (SQLStatementInspector) scope.getStatementInspector();
		statementInspector.clear();
		scope.inTransaction(
				session -> {
					System system = session.createQuery( "from System e join e.user where e.id = :id", System.class )
							.setParameter( "id", 1 ).uniqueResult();
					statementInspector.assertExecutedCount( 3 );
					statementInspector.assertNumberOfOccurrenceInQuery( 0, "join", 1 );
					statementInspector.assertNumberOfOccurrenceInQuery( 1, "join", 0 );
					statementInspector.assertNumberOfOccurrenceInQuery( 2, "join", 0 );
					assertThat( system, is( notNullValue() ) );
					SystemUser user = system.getUser();
					assertThat( user, is( notNullValue() ) );
				}
		);
	}

	@Test
	public void testHqlJoinFetch(SessionFactoryScope scope) {
		SQLStatementInspector statementInspector = (SQLStatementInspector) scope.getStatementInspector();
		statementInspector.clear();
		scope.inTransaction(
				session -> {
					System system = session.createQuery(
							"from System e join fetch e.user where e.id = :id",
							System.class
					)
							.setParameter( "id", 1 ).uniqueResult();
					statementInspector.assertExecutedCount( 2 );
					statementInspector.assertNumberOfOccurrenceInQuery( 0, "join", 1 );
					statementInspector.assertNumberOfOccurrenceInQuery( 1, "join", 0 );
					assertThat( system, is( notNullValue() ) );
					SystemUser user = system.getUser();
					assertThat( user, is( notNullValue() ) );
				}
		);
	}

	@Test
	@FailureExpected(reason = "Embedded parameters has not yet been implemented ")
	public void testEmbeddedIdParameter(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Subsystem subsystem = new Subsystem( 2, "sub1" );

					PK superUserKey = new PK( subsystem, "Fab" );

					System system = session.createQuery(
							"from System e join fetch e.user u where u.id = :id",
							System.class
					).setParameter( "id", superUserKey ).uniqueResult();

					assertThat( system, is( notNullValue() ) );
				}
		);
	}

	@Test
	public void testHql2(SessionFactoryScope scope) {
		SQLStatementInspector statementInspector = (SQLStatementInspector) scope.getStatementInspector();
		statementInspector.clear();
		/*
		  select
			s1_0.subsystem_id,
			s1_0.username,
			s1_0.name
		from
			SystemUser as s1_0

        select
			s1_0.id,
			s1_0.description
		from
			Subsystem s1_0
		where
			s1_0.id=?
		 */
		scope.inTransaction(
				session -> {
					SystemUser system = (SystemUser) session.createQuery( "from SystemUser " )
							.uniqueResult();
					assertThat( system, is( notNullValue() ) );

					statementInspector.assertExecutedCount( 2 );
					statementInspector.assertNumberOfOccurrenceInQuery( 0, "join", 0 );
					statementInspector.assertNumberOfOccurrenceInQuery( 1, "join", 0 );

					assertTrue( Hibernate.isInitialized( system.getPk().subsystem ) );

				}
		);
	}


	@Entity(name = "System")
	public static class System {
		@Id
		private Integer id;
		private String name;

		@ManyToOne
		SystemUser user;

		public System() {
		}

		public System(Integer id, String name) {
			this.id = id;
			this.name = name;
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public SystemUser getUser() {
			return user;
		}

		public void setUser(SystemUser user) {
			this.user = user;
		}
	}

	@Entity(name = "SystemUser")
	public static class SystemUser {

		@EmbeddedId
		private PK pk;

//		private String name;

		public SystemUser() {
		}

		public SystemUser(PK pk, String name) {
			this.pk = pk;
//			this.name = name;
		}

		public PK getPk() {
			return pk;
		}

		public void setPk(PK pk) {
			this.pk = pk;
		}

//		public String getName() {
//			return name;
//		}

//		public void setName(String name) {
//			this.name = name;
//		}
	}

	@Embeddable
	public static class PK implements Serializable {

		@ManyToOne
		private Subsystem subsystem;

		private String username;

		public PK(Subsystem subsystem, String username) {
			this.subsystem = subsystem;
			this.username = username;
		}

		private PK() {
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}
			PK pk = (PK) o;
			return Objects.equals( subsystem, pk.subsystem ) &&
					Objects.equals( username, pk.username );
		}

		@Override
		public int hashCode() {
			return Objects.hash( subsystem, username );
		}
	}

	@Entity(name = "Subsystem")
	public static class Subsystem {

		@Id
		private Integer id;

		private String description;

		public Subsystem() {
		}

		public Subsystem(Integer id, String description) {
			this.id = id;
			this.description = description;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

//		public Integer getId() {
//			return id;
//		}
	}
}
